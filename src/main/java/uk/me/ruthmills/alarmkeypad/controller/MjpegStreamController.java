package uk.me.ruthmills.alarmkeypad.controller;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * MJPEG stream controller.
 * 
 * @author ruth
 */
@Controller
public class MjpegStreamController {

	// MJPEG multipart boundary stuff.
	private static final int INPUT_BUFFER_SIZE = 16384;
	private static final String NL = "\r\n";
	private static final String BOUNDARY = "--boundary";
	private static final String HEAD = NL + NL + BOUNDARY + NL + "Content-Type: image/jpeg" + NL + "Content-Length: ";

	private URLConnection conn;
	private ByteArrayOutputStream byteArrayOutputStream;
	private byte[] currentFrame = new byte[0];
	private String nowFormatted;

	@Value("${streamURL}")
	private String streamURL;
	private static final Logger logger = LoggerFactory.getLogger(MjpegStreamController.class);

	/**
	 * Get the MJPEG stream.
	 * 
	 * IMPORTANT - this will ONLY work for a SINGLE client connecting to this
	 * application!
	 * 
	 * @return The MJPEG stream.
	 */
	@GetMapping(path = "/mjpeg_stream", produces = "multipart/x-mixed-replace;boundary=" + BOUNDARY)
	public StreamingResponseBody getMjpegStream() {

		return new StreamingResponseBody() {

			@Override
			public void writeTo(OutputStream outputStream) throws IOException {
				try {
					// Continue until the connection drops.
					while (true) {
						try (InputStream inputStream = openConnection()) {
							int prev = 0;
							int cur = 0;

							// EOF is -1
							while ((inputStream != null) && ((cur = inputStream.read()) >= 0)) {
								if (prev == 0xFF && cur == 0xD8) {
									LocalDateTime now = LocalDateTime.now();
									nowFormatted = Long.toString(now.toInstant(ZoneOffset.UTC).toEpochMilli());
									byteArrayOutputStream = new ByteArrayOutputStream(INPUT_BUFFER_SIZE);
									byteArrayOutputStream.write((byte) prev);
								}
								if (byteArrayOutputStream != null) {
									byteArrayOutputStream.write((byte) cur);
									if (prev == 0xFF && cur == 0xD9) {
										synchronized (currentFrame) {
											currentFrame = byteArrayOutputStream.toByteArray();
										}
										byteArrayOutputStream.close();
										// the image is now available - read it
										handleNewFrame(outputStream);
									}
								}
								prev = cur;
							}
						} catch (Exception ex) {
							logger.error("Failed to read stream", ex);
						}
					}
				} catch (Exception ex) {
					logger.error("Exception when writing output stream", ex);
				}
			}
		};
	}

	private BufferedInputStream openConnection() throws IOException {
		BufferedInputStream bufferedInputStream = null;
		URL url = new URL(streamURL);
		conn = url.openConnection();
		conn.setReadTimeout(5000); // 5 seconds
		conn.connect();
		bufferedInputStream = new BufferedInputStream(conn.getInputStream(), INPUT_BUFFER_SIZE);
		return bufferedInputStream;
	}

	private void handleNewFrame(OutputStream outputStream) {
		try {
			JpegImageMetadata imageMetadata = (JpegImageMetadata) Imaging.getMetadata(currentFrame);
			TiffImageMetadata exif = imageMetadata.getExif();
			TiffOutputSet outputSet = exif.getOutputSet();
			final TiffOutputDirectory exifDirectory = outputSet.getOrCreateExifDirectory();

			// Use the Owner Name tag to store the timestamp in milliseconds.
			exifDirectory.add(ExifTagConstants.EXIF_TAG_OWNER_NAME, nowFormatted);
			try (ByteArrayOutputStream exifOutputStream = new ByteArrayOutputStream(INPUT_BUFFER_SIZE)) {
				// Create a copy of the JPEG image with EXIF metadata added.
				new ExifRewriter().updateExifMetadataLossy(currentFrame, exifOutputStream, outputSet);

				byte[] image = exifOutputStream.toByteArray();

				// Write the MJPEG header stuff.
				outputStream.write((HEAD + image.length + NL + NL).getBytes());

				// Write the EXIF-ed image.
				outputStream.write(image);

				// Flush the loo.
				outputStream.flush();
			}
		} catch (Exception ex) {
			logger.error("Exception when adding EXIF metadata", ex);
		}
	}
}
