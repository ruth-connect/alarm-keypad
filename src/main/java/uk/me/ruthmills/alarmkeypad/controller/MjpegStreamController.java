package uk.me.ruthmills.alarmkeypad.controller;

import java.io.IOException;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import uk.me.ruthmills.alarmkeypad.service.ImageService;

/**
 * MJPEG stream controller.
 * 
 * This is a HORRIBLE hack - as it will only work for ONE client connecting at
 * once.
 * 
 * But it will work for our purposes, as only one client will ever connect at
 * once anyway.
 * 
 * @author ruth
 */
@Controller
public class MjpegStreamController {

	// MJPEG multipart boundary stuff.
	private static final String NL = "\r\n";
	private static final String BOUNDARY = "--boundary";
	private static final String HEAD = NL + NL + BOUNDARY + NL + "Content-Type: image/jpeg" + NL + "Content-Length: ";

	@Autowired
	private ImageService imageService;

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
				// Grab the first image from the stream and ditch it, because it will have an
				// old timestamp.
				try {
					imageService.getNextImage();
				} catch (InterruptedException ex) {
					logger.error("Interrupted Exception", ex);
				}

				try {
					// Continue until the connection drops.
					while (true) {
						try {
							// Wait for the next EXIF-ed image from the camera.
							byte[] image = imageService.getNextImage();

							// Write the MJPEG header stuff.
							outputStream.write((HEAD + image.length + NL + NL).getBytes());

							// Write the EXIF-ed image.
							outputStream.write(image);
						} catch (InterruptedException ex) {
							logger.error("Interrupted Exception", ex);
						}
					}
				} catch (Exception ex) {
					logger.error("Exception when writing output stream", ex);
				}
			}
		};
	}
}
