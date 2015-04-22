package helpers;

import static com.googlecode.javacv.cpp.opencv_core.CV_AA;
import static com.googlecode.javacv.cpp.opencv_core.cvLine;

import java.util.ArrayList;
import java.util.Collections;

import javax.swing.JFrame;
import javax.swing.JTextField;

import server.utils.CircleMarker;
import server.utils.HSVColor;
import server.utils.PixelOperations;

import com.googlecode.javacv.cpp.opencv_core.CvPoint;
import com.googlecode.javacv.cpp.opencv_core.CvScalar;
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
	
	JFrame window = new JFrame("Color");
	JTextField colorA = new JTextField(3);
	
	
	public CalibrationThread(CalibrateColors calibrateColors) {
		this.calibrateColors = calibrateColors;

		hsvList = new ArrayList<HSVColor>();
		
		window.getContentPane().add(colorA);
		window.pack();
		window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		window.setVisible(true);
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

				CvPoint p = new CvPoint(1124,100);
				cvLine(image, p, p, CvScalar.WHITE, 3, CV_AA, 0);
				
				double[] hsv = getHSVMarkerColor(image, (int)marker.getX(), (int)marker.getY());
				double[] corner = PixelOperations.getPixelHSV(image,p.x(),p.y());
				
//				colorA.setBackground(PixelOperations.getHSVColor((int)corner[0], (int)corner[1], (int)corner[2]));
				
				hsvList.add(new HSVColor(hsv[0], hsv[1], Math.min(hsv[2] + 100 - corner[2],100)));

				calibrateColors.incrementThreadsDone();
			}

		} catch (Exception e) {
			// e.printStackTrace();
			System.out.println(Thread.currentThread().getName() + " was interrupeted!");
			window.dispose();
		}
	}

	private double[] getHSVMarkerColor(IplImage img, int markerX, int markerY){
		ArrayList<HSVColor> colors = new ArrayList<HSVColor>();
		
		double[] hsv = PixelOperations.getPixelHSV(img, markerX, markerY);
		colors.add(new HSVColor(hsv[0], hsv[1], hsv[2]));
		
		double[] hsv2 = PixelOperations.getPixelHSV(img, markerX, markerY - 10);
		colors.add(new HSVColor(hsv2[0], hsv2[1], hsv2[2]));
		
		double[] hsv3 = PixelOperations.getPixelHSV(img, markerX - 10, markerY - 10);
		colors.add(new HSVColor(hsv3[0], hsv3[1], hsv3[2]));
		
		double[] hsv4 = PixelOperations.getPixelHSV(img, markerX + 10, markerY - 10);
		colors.add(new HSVColor(hsv4[0], hsv4[1], hsv4[2]));
		
		double[] hsv5 = PixelOperations.getPixelHSV(img, markerX - 10, markerY);
		colors.add(new HSVColor(hsv5[0], hsv5[1], hsv5[2]));
		
		double[] hsv6 = PixelOperations.getPixelHSV(img, markerX + 10, markerY);
		colors.add(new HSVColor(hsv6[0], hsv6[1], hsv6[2]));
		
		double[] hsv7 = PixelOperations.getPixelHSV(img, markerX, markerY + 10);
		colors.add(new HSVColor(hsv7[0], hsv7[1], hsv7[2]));
		
		double[] hsv8 = PixelOperations.getPixelHSV(img, markerX - 10, markerY + 10);
		colors.add(new HSVColor(hsv8[0], hsv8[1], hsv8[2]));
		
		double[] hsv9 = PixelOperations.getPixelHSV(img, markerX + 10, markerY + 10);
		colors.add(new HSVColor(hsv9[0], hsv9[1], hsv9[2]));
		
		double hue = 0;
		double saturation = 0;
		double brightness = 0;
		int size = 0;
		
		for (HSVColor c : colors) {
			if(c.getBrightness() > 20){
				hue += c.getHue();
				saturation += c.getSaturation();
				brightness += c.getBrightness();
				size++;
			}
		}
		
		hue /= size;
		saturation /= size;
		brightness /= size;
		
		return new double[] {hue,saturation,brightness};
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
