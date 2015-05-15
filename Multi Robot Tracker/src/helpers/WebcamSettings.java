package helpers;

import static com.googlecode.javacv.cpp.opencv_core.*;
import static com.googlecode.javacv.cpp.opencv_imgproc.*;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.JPanel;

import server.utils.CircleMarker;
import server.utils.HSVColor;
import server.utils.PixelOperations;

import com.googlecode.javacv.CanvasFrame;
import com.googlecode.javacv.cpp.opencv_core.CvMemStorage;
import com.googlecode.javacv.cpp.opencv_core.CvPoint;
import com.googlecode.javacv.cpp.opencv_core.CvPoint3D32f;
import com.googlecode.javacv.cpp.opencv_core.CvScalar;
import com.googlecode.javacv.cpp.opencv_core.CvSeq;
import com.googlecode.javacv.cpp.opencv_core.IplImage;
import com.googlecode.javacv.cpp.opencv_highgui;
import com.googlecode.javacv.cpp.opencv_highgui.CvCapture;

public class WebcamSettings extends Thread {
	
	private CanvasFrame canvasOriginal;
	private CanvasFrame canvasDetection;
	private JFrame detectingColors;
	private IplImage image;
		
	public WebcamSettings() {
		canvasOriginal = new CanvasFrame("Camera");
		canvasDetection = new CanvasFrame("Detecting");
		canvasOriginal.setPreferredSize(new Dimension(600, 400));
		canvasDetection.setPreferredSize(new Dimension(600, 400));
		canvasDetection.setLocation(600, 0);
		
		detectingColors = new JFrame("Detecting Colors");
		detectingColors.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		detectingColors.setLocationRelativeTo(null);
		detectingColors.setSize(100, 100);
		detectingColors.setVisible(true);
	}
	
	@Override
	public void run() {
		
		CvCapture capture = opencv_highgui.cvCreateCameraCapture(0);
		
		opencv_highgui.cvSetCaptureProperty(capture, opencv_highgui.CV_CAP_PROP_FRAME_HEIGHT, 960);
        opencv_highgui.cvSetCaptureProperty(capture, opencv_highgui.CV_CAP_PROP_FRAME_WIDTH, 1280);
        
        while (true) {
			if((image = opencv_highgui.cvQueryFrame(capture)) == null) {
				break;
			} else {
				setCameraSettings(capture);
				calibration();
			}
		}
	}
	
	private void calibration() {
//		CvRect roi = cvRect(40, 85, 1160, 730);
		
//		cvSetImageROI(image, roi);
		IplImage croppedImage = IplImage.create(cvSize(image.width(), image.height()), 8, 3);
		cvCopy(image, croppedImage);
//		cvResetImageROI(image);

//		canvas.showImage(croppedImage);
		
		IplImage imageGray = IplImage.create(cvGetSize(croppedImage), 8, 1);

		cvCvtColor(croppedImage, imageGray, CV_RGB2GRAY);
//		canvas.showImage(imageGray);
		
		CvMemStorage storage = cvCreateMemStorage(0);

		cvThreshold(imageGray, imageGray, 115, 255, CV_THRESH_BINARY); // 100-255 - 20-255
//		canvas.showImage(imageGray);
		
//		cvNot(imageGray, imageGray);
//		canvas.showImage(imageGray);
		
//		cvDilate(imageGray, imageGray, null, 5);
//		canvas.showImage(imageGray);
		
//		cvErode(imageGray, imageGray, null, 5);
		
		cvSmooth(imageGray, imageGray, CV_GAUSSIAN, 3);
		canvasDetection.showImage(imageGray);
		
		cvCanny(imageGray, imageGray, 100, 100, 3);// 100 100 3
//		canvas.showImage(imageGray);
		
		// Find circles
		CvSeq circles = cvHoughCircles(imageGray, // Input image
				storage, // Memory Storage
				CV_HOUGH_GRADIENT, // Detection method
				1, // Inverse ratio
				100, // Minimum distance between the centers of the detected
						// circles - 100
				10, // Higher threshold for canny edge detector - 10
				15, // Threshold at the center detection stage - 15
				15, // min radius - 15
				30 // max radius - 30
		);

		int numberOfCircles = circles.total();		
		if(numberOfCircles == 0)
			System.out.println("Not detecting any circle");
		
		getMarkerColor(croppedImage, circles, numberOfCircles);
		
		cvReleaseMemStorage(circles.storage());
		
		imageGray.release();
		croppedImage.release();
	}

	private void getMarkerColor(IplImage croppedImage, CvSeq circles, int numberOfCircles) {
		detectingColors.getContentPane().removeAll();
		detectingColors.getContentPane().setLayout(new GridLayout(1, numberOfCircles));
		
		for (int i = 0; i < numberOfCircles; i++) {
			ArrayList<HSVColor> colors = obtainMakerColorSamples(croppedImage, circles, i, false);
			
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
			
			double hue = Math.atan2(hueY, hueX) * 180 / Math.PI;
			saturation /= size;
			brightness /= size;
			
			JPanel p = new JPanel();
			p.setBackground(PixelOperations.getHSVColor(hue, saturation, brightness));
			detectingColors.getContentPane().add(p);
			
			System.out.println("H: " + hue + ", S: " + saturation +  "V: " + brightness);
			
			canvasOriginal.showImage(croppedImage);
		}
		detectingColors.validate();
		System.out.println(" ------- ");
	}

	private ArrayList<HSVColor> obtainMakerColorSamples(IplImage croppedImage, CvSeq circles, int i, boolean seePoints) {
		ArrayList<HSVColor> colors = new ArrayList<HSVColor>();
		ArrayList<CvPoint> points = new ArrayList<CvPoint>();
		
		CvPoint3D32f point3D = new CvPoint3D32f(cvGetSeqElem(circles, i));
		CircleMarker circle = new CircleMarker(point3D.x(), point3D.y(),point3D.z());
		CvPoint3D32f.deallocateReferences();
		
		int sampleRange = 2;
		
		CvPoint p1 = cvPoint((int)circle.getX(), (int)circle.getY());
		double[] hsv = PixelOperations.getPixelHSV(croppedImage, p1.x(),p1.y());
		colors.add(new HSVColor(hsv[0], hsv[1], hsv[2]));
		points.add(p1);
		
		CvPoint p2 = cvPoint((int)circle.getX(), (int)circle.getY()-sampleRange);
		double[] hsv2 = PixelOperations.getPixelHSV(croppedImage, p2.x(), p2.y());
		colors.add(new HSVColor(hsv2[0], hsv2[1], hsv2[2]));
		points.add(p2);
		
		CvPoint p3 = cvPoint((int)circle.getX()-sampleRange,(int)circle.getY()-sampleRange);
		double[] hsv3 = PixelOperations.getPixelHSV(croppedImage, p3.x(), p3.y());
		colors.add(new HSVColor(hsv3[0], hsv3[1], hsv3[2]));
		points.add(p3);
		
		CvPoint p4 = cvPoint((int)circle.getX()+sampleRange,(int)circle.getY()-sampleRange);
		double[] hsv4 = PixelOperations.getPixelHSV(croppedImage, p4.x(), p4.y());
		colors.add(new HSVColor(hsv4[0], hsv4[1], hsv4[2]));
		points.add(p4);
		
		CvPoint p5 = cvPoint((int)circle.getX()-sampleRange,(int)circle.getY());
		double[] hsv5 = PixelOperations.getPixelHSV(croppedImage, p5.x(), p5.y());
		colors.add(new HSVColor(hsv5[0], hsv5[1], hsv5[2]));
		points.add(p5);
		
		CvPoint p6 = cvPoint((int)circle.getX()+sampleRange,(int)circle.getY());
		double[] hsv6 = PixelOperations.getPixelHSV(croppedImage, p6.x(), p6.y());
		colors.add(new HSVColor(hsv6[0], hsv6[1], hsv6[2]));
		points.add(p6);
		
		CvPoint p7 = cvPoint((int)circle.getX(),(int)circle.getY()+sampleRange);
		double[] hsv7 = PixelOperations.getPixelHSV(croppedImage, p7.x(), p7.y());
		colors.add(new HSVColor(hsv7[0], hsv7[1], hsv7[2]));
		points.add(p7);
		
		CvPoint p8 = cvPoint((int)circle.getX()-sampleRange,(int)circle.getY()+sampleRange);
		double[] hsv8 = PixelOperations.getPixelHSV(croppedImage, p8.x(), p8.y());
		colors.add(new HSVColor(hsv8[0], hsv8[1], hsv8[2]));
		points.add(p8);
		
		CvPoint p9 = cvPoint((int)circle.getX()+sampleRange,(int)circle.getY()+sampleRange);
		double[] hsv9 = PixelOperations.getPixelHSV(croppedImage, p9.x(), p9.y());
		colors.add(new HSVColor(hsv9[0], hsv9[1], hsv9[2]));
		points.add(p9);
		
		if(seePoints)
			seeColorCollectingPoints(croppedImage, points);
		
		return colors;
	}

	private void seeColorCollectingPoints(IplImage image, ArrayList<CvPoint> points) {
		for (CvPoint cvPoint : points)
			cvLine(image, cvPoint, cvPoint, CvScalar.RED, 5, CV_AA, 0);
	}
	
	@SuppressWarnings("unused")
	private void drawPoint(CvPoint p){
		cvLine(image, p, p, CvScalar.BLACK, 3, CV_AA, 0);
	}
	
	private void setCameraSettings(CvCapture capture){
		ArrayList<HSVColor> avgColor = new ArrayList<HSVColor>();
		
		for (int i = 1130; i < 1150; i++) {
			for (int j = 55; j < 75; j++) {
				double[] hsv = PixelOperations.getPixelHSV(image, 1140, 65);
				avgColor.add(new HSVColor(hsv[0], hsv[1], hsv[2]));
			}
		}
		
		int avgH = 0;
		int avgS = 0;
		int avgV = 0;
		
		for (HSVColor hsvColor : avgColor) {
			avgH += hsvColor.getHue();
			avgS += hsvColor.getSaturation();
			avgV += hsvColor.getBrightness();
		}
		
		avgH = avgH/avgColor.size();
		avgS = avgS/avgColor.size();
		avgV = avgV/avgColor.size();
		
//		canvas.showImage(image);
	}
	
	public static void main(String[] args) {
		new WebcamSettings().start();
	}
	
}
