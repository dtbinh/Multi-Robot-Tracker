package helpers;

import java.util.ArrayList;
import java.util.Collections;

import server.utils.CircleMarker;
import server.utils.HSVColor;
import server.utils.PixelOperations;

import com.googlecode.javacv.cpp.opencv_core.IplImage;

public class CalibrationThread extends Thread {
	 private static final int removePercentage = 5;

	private CircleMarker marker;
	private IplImage image;

	private ArrayList<HSVColor> hsvList;

	private double minH = Double.MAX_VALUE;
	private double minS = Double.MAX_VALUE;
	private double minV = Double.MAX_VALUE;

	private double maxH = Double.MIN_VALUE;
	private double maxS = Double.MIN_VALUE;
	private double maxV = Double.MIN_VALUE;

	private boolean working = false;
	private int numberOfComputations = 0;

	private CalibrateColors calibrateColors;
	
	
	public CalibrationThread(CalibrateColors calibrateColors) {
		this.calibrateColors = calibrateColors;

		hsvList = new ArrayList<HSVColor>();
	}

	@Override
	public void run() {
		try {
			while (true) {
				while (!working) {
					synchronized (this) {
						wait();
//						System.out.println(Thread.currentThread().getName() + " is waiting ...");
					}
				}
				working = false;

//				System.out.println(Thread.currentThread().getName() + " is working!");
				numberOfComputations++;

				double[] hsv = getHSVMarkerColor(image, marker);
				hsvList.add(new HSVColor(hsv[0], hsv[1], hsv[2]));

				calibrateColors.incrementThreadsDone();
			}

		} catch (Exception e) {
			// e.printStackTrace();
			System.out.println(Thread.currentThread().getName() + " was interrupted!");
		}
	}

	private double[] getHSVMarkerColor(IplImage img, CircleMarker circle){
		ArrayList<HSVColor> colors = obtainMakerColorSamples(img, circle);
		
		double hueX = 0;
		double hueY = 0;
		double saturation = 0;
		double brightness = 0;
		double size = 0;
		
		for (HSVColor c : colors) {
			hueX += Math.cos(c.getHue() / 180 * Math.PI);
			hueY += Math.sin(c.getHue() / 180 * Math.PI);
			saturation += c.getSaturation();
			brightness += c.getBrightness();
			size++;
		}
		
		hueX /= size;
		hueY /= size;
		saturation /= size;
		brightness /= size;
		
		double hue = Math.atan2(hueY, hueX) * 180 / Math.PI;
		
		return new double[] {hue,saturation,brightness};
	}

	private ArrayList<HSVColor> obtainMakerColorSamples(IplImage img, CircleMarker circle) {
		ArrayList<HSVColor> colors = new ArrayList<HSVColor>();
		
		int sampleRange = 2;
		
		double[] hsv = PixelOperations.getPixelHSV(img, (int)circle.getX(),(int)circle.getY());
		colors.add(new HSVColor(hsv[0], hsv[1], hsv[2]));
		
		double[] hsv2 = PixelOperations.getPixelHSV(img, (int)circle.getX(),(int)circle.getY()-sampleRange);
		colors.add(new HSVColor(hsv2[0], hsv2[1], hsv2[2]));
		
		double[] hsv3 = PixelOperations.getPixelHSV(img, (int)circle.getX()-sampleRange,(int)circle.getY()-sampleRange);
		colors.add(new HSVColor(hsv3[0], hsv3[1], hsv3[2]));
		
		double[] hsv4 = PixelOperations.getPixelHSV(img, (int)circle.getX()+sampleRange,(int)circle.getY()-sampleRange);
		colors.add(new HSVColor(hsv4[0], hsv4[1], hsv4[2]));
		
		double[] hsv5 = PixelOperations.getPixelHSV(img, (int)circle.getX()-sampleRange,(int)circle.getY());
		colors.add(new HSVColor(hsv5[0], hsv5[1], hsv5[2]));
		
		double[] hsv6 = PixelOperations.getPixelHSV(img, (int)circle.getX()+sampleRange,(int)circle.getY());
		colors.add(new HSVColor(hsv6[0], hsv6[1], hsv6[2]));
		
		double[] hsv7 = PixelOperations.getPixelHSV(img, (int)circle.getX(),(int)circle.getY()+sampleRange);
		colors.add(new HSVColor(hsv7[0], hsv7[1], hsv7[2]));
		
		double[] hsv8 = PixelOperations.getPixelHSV(img, (int)circle.getX()-sampleRange,(int)circle.getY()+sampleRange);
		colors.add(new HSVColor(hsv8[0], hsv8[1], hsv8[2]));
		
		double[] hsv9 = PixelOperations.getPixelHSV(img, (int)circle.getX()+sampleRange,(int)circle.getY()+sampleRange);
		colors.add(new HSVColor(hsv9[0], hsv9[1], hsv9[2]));
		
		return colors;
	}
	
	public synchronized void setParameters(IplImage image, CircleMarker marker) {
		this.image = image;
		this.marker = marker;
		working = true;
//		System.out.println(Thread.currentThread().getName() + " notified " + this.getName() + "!");
		notifyAll();
	}

	public int[] calculateResult() {
		int[] result = new int[6];

		Collections.sort(hsvList);
		
		int topLimit = Math.round(hsvList.size() * (removePercentage/100));
//		int bottomLimit = Math.round((hsvList.size() * (100 - removePercentage)/100));
		
		for (int i = 0; i < topLimit; i++) {
			hsvList.remove(i);
		}
		
//		for (int i = bottomLimit; i < hsvList.size(); i++) {
//			hsvList.remove(i);
//		}
	
		// obter minimo/máximo para H, S e V.
		for (HSVColor hsv : hsvList) {
			
			double h = hsv.getHue();
			double s = hsv.getSaturation();
			double b = hsv.getBrightness();
			
			if (h < minH)
				minH = h;
			
			if (h > maxH)
				maxH = h;
			
			if (s < minS)
				minS = s;
			
			if (s > maxS)
				maxS = s;
			
			if (b < minV)
				minV = b;
			
			if (b > maxV)
				maxV = b;
			
		}
	
		// criar a linha da matriz
		result[0] = (int) minH;
		result[1] = (int) Math.round(maxH);
		result[2] = (int) minS;
		result[3] = (int) Math.round(maxS);
		result[4] = (int) minV;
		result[5] = (int) Math.round(maxV);
	
		return result;
	}

	public int getNumberOfComputations() {
		return numberOfComputations;
	}

	public CircleMarker getMarker() {
		return marker;
	}

	public int[] getResult() {
		return calculateResult();
	}

	public ArrayList<HSVColor> getHsvList() {
		return hsvList;
	}
	
}
