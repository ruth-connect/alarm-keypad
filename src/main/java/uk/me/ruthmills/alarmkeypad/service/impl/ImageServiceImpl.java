package uk.me.ruthmills.alarmkeypad.service.impl;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.springframework.stereotype.Service;

import uk.me.ruthmills.alarmkeypad.service.ImageService;

/**
 * Image service implementation. Stores the next EXIF-ed image from the camera.
 * 
 * @author ruth
 */
@Service
public class ImageServiceImpl implements ImageService {

	// Blocking queue that will only store one image at once.
	private BlockingQueue<byte[]> imageQueue = new ArrayBlockingQueue<>(1);

	/**
	 * Gets the next image from the MJPEG stream. Blocks until the next image is
	 * available.
	 * 
	 * @return The next image from the MJPEG stream.
	 * @throws InterruptedException Thrown if the thread was interrupted.
	 */
	@Override
	public byte[] getNextImage() throws InterruptedException {
		// Get the next image from the MJPEG stream. Block until we get one.
		return imageQueue.take();
	}

	/**
	 * Sets the next image from the MJPEG stream.
	 * 
	 * @param image The next image from the MJPEG stream.
	 */
	@Override
	public void setNextImage(byte[] image) {
		// Offer the current image to the queue. If there is an image in the queue
		// already, then we keep it there, and don't add this new one to the queue as
		// well.
		imageQueue.offer(image);
	}
}
