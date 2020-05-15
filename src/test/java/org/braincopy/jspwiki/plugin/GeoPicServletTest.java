package org.braincopy.jspwiki.plugin;

import java.io.IOException;

import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.Imaging;
import org.junit.jupiter.api.Test;

//import junit.framework.TestCase;

public class GeoPicServletTest {

	@Test
	public void testResizeImage() {
		System.out.println("test");
		//tempDir deleteOnExit();
		try {
			final java.awt.image.BufferedImage image = Imaging.getBufferedImage(new java.io.File(""));
		} catch (ImageReadException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}