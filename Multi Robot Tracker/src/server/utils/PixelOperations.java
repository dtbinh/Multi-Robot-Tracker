package server.utils;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;

import com.googlecode.javacv.cpp.opencv_core.IplImage;

	public class PixelOperations {
		
		/**
		 * 
		 * @param image - color image
		 * @param x - x position of the pixel
		 * @param y - y position of the pixel
		 * 
		 * @return array of integers, where red=0, green=1, blue=2
		 */
		public static int[] getPixelRGB(IplImage image, int x, int y) {
			int[] rgb = new int[3];
			ByteBuffer ImageBuffer = image.getByteBuffer();
		    
	        int indexB =  y * image.widthStep() + x * image.nChannels() + 0;
	        int indexG =  y * image.widthStep() + x * image.nChannels() + 1;
	        int indexR =  y * image.widthStep() + x * image.nChannels() + 2;
	        
	        // Read the pixel value - the 0xFF is needed to cast 
	        // from an unsigned byte to an int.
	        rgb[0] = ImageBuffer.get(indexR) & 0xFF;
	        rgb[1] = ImageBuffer.get(indexG) & 0xFF;
	        rgb[2] = ImageBuffer.get(indexB) & 0xFF;

//	        System.out.println("RGB -> R:" + rgb[0] + " G:" + rgb[1] + " B:" + rgb[2]);
	        
	        return rgb;
		}
		
		//hue=0, saturation=1, value=2
		/**
		 * 
		 * @param image - color image
		 * @param x - x position of the pixel
		 * @param y - y position of the pixel
		 * @return array of doubles, where hue=0, saturation=1, value=2
		 */
		public static double[] getPixelHSV(IplImage image, int x, int y) {
			double[] hsv = new double[3];
			int[] rgb = getPixelRGB(image, x, y);
			
			int r = rgb[0];
	        int g = rgb[1];
	        int b = rgb[2];

	        float[] aux = new float[3];
			Color.RGBtoHSB(r, g, b, aux);
			
			DecimalFormat df = new DecimalFormat("#.0"); 
			
	        hsv[0] = Math.round(aux[0]*360);
	        hsv[1] = Double.valueOf(df.format(aux[1]*100));
	        hsv[2] = Double.valueOf(df.format(aux[2]*100));
			
//			System.out.println(hsv[0] + "\t " + hsv[1] + "\t " + hsv[2]);
	        
	        return hsv;
		}
			
		/**
		 * Return the color of a pixel HSV
		 * 
		 * @param hue
		 * @param saturation
		 * @param brightness
		 * @return
		 */
		public static Color getHSVColor(double hue, double saturation, double brightness){
			float newH = (float)hue/360;
			float newS = (float)saturation/100;
			float newV = (float)brightness/100;
			
			int rgbId = Color.HSBtoRGB(newH, newS, newV);
			
			return new Color(rgbId);
		}
		
		
		
}
