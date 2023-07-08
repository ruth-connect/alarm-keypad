package uk.me.ruthmills.alarmkeypad.service;

/**
 * Image service. Stores the next EXIF-ed image from the camera.
 * 
 * @author ruth
 */
public interface ImageService {

	/**
	 * Gets the next image from the MJPEG stream. Blocks until the next image is
	 * available.
	 * 
	 * @return The next image from the MJPEG stream.
	 * @throws InterruptedException Thrown if the thread was interrupted.
	 */
	public byte[] getNextImage() throws InterruptedException;

	/**
	 * Sets the next image from the MJPEG stream.
	 * 
	 * @param image The next image from the MJPEG stream.
	 */
	public void setNextImage(byte[] image);
}
