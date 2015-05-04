package helpers;

import static com.googlecode.javacv.cpp.opencv_core.CV_AA;
import static com.googlecode.javacv.cpp.opencv_core.*;
import static com.googlecode.javacv.cpp.opencv_imgproc.CV_HOUGH_GRADIENT;
import static com.googlecode.javacv.cpp.opencv_imgproc.CV_RGB2GRAY;
import static com.googlecode.javacv.cpp.opencv_imgproc.CV_THRESH_BINARY;
import static com.googlecode.javacv.cpp.opencv_imgproc.*;

import java.awt.Dimension;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.JTextField;

import server.utils.CircleMarker;
import server.utils.HSVColor;
import server.utils.PixelOperations;

import com.googlecode.javacv.CanvasFrame;
import com.googlecode.javacv.cpp.opencv_core.CvMemStorage;
import com.googlecode.javacv.cpp.opencv_core.CvPoint;
import com.googlecode.javacv.cpp.opencv_core.CvPoint3D32f;
import com.googlecode.javacv.cpp.opencv_core.CvRect;
import com.googlecode.javacv.cpp.opencv_core.CvScalar;
import com.googlecode.javacv.cpp.opencv_core.CvSeq;
import com.googlecode.javacv.cpp.opencv_core.IplImage;
import com.googlecode.javacv.cpp.opencv_highgui;
import com.googlecode.javacv.cpp.opencv_highgui.CvCapture;

public class WebcamSettings extends Thread {
	
	private CanvasFrame canvas;
	private IplImage image;
		
	JFrame window = new JFrame("Color");
	JTextField colorA = new JTextField(3);
	
	public WebcamSettings() {
		canvas = new CanvasFrame("Camera");
		canvas.setPreferredSize(new Dimension(600, 400));
				
		window.getContentPane().add(colorA);
		window.pack();
		window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		window.setLocationRelativeTo(null);
		window.setVisible(true);
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
		CvRect roi = cvRect(40, 85, 1160, 730);
		
		cvSetImageROI(image, roi);
		IplImage croppedImage = IplImage.create(cvSize(roi.width(), roi.height()), 8, 3);
		cvCopy(image, croppedImage);
		cvResetImageROI(image);

//		canvas.showImage(croppedImage);
		
		IplImage imageGray = IplImage.create(cvGetSize(croppedImage), 8, 1);

		cvCvtColor(croppedImage, imageGray, CV_RGB2GRAY);
//		canvas.showImage(imageGray);
		
		CvMemStorage storage = cvCreateMemStorage(0);

		cvThreshold(imageGray, imageGray, 20, 255, CV_THRESH_BINARY); // 100-255 - 20-255
//		canvas.showImage(imageGray);
		
		cvNot(imageGray, imageGray);
//		canvas.showImage(imageGray);
		
		cvDilate(imageGray, imageGray, null, 5);
//		canvas.showImage(imageGray);
		
		cvErode(imageGray, imageGray, null, 5);
		canvas.showImage(imageGray);
		
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
		System.out.println(numberOfCircles);
		
		System.out.println("Colors Obtained: ");
		for (int i = 0; i < numberOfCircles; i++) {
			ArrayList<HSVColor> colors = new ArrayList<HSVColor>();
			
			CvPoint3D32f point3D = new CvPoint3D32f(cvGetSeqElem(circles, i));
			CircleMarker circle = new CircleMarker(point3D.x(), point3D.y(),point3D.z());
			CvPoint3D32f.deallocateReferences();
			
			int sampleRange = 2;
			
			double[] hsv = PixelOperations.getPixelHSV(croppedImage, (int)circle.getX(),(int)circle.getY());
			colors.add(new HSVColor(hsv[0], hsv[1], hsv[2]));
//			cvLine(croppedImage, cvPoint((int)circle.getX(), (int)circle.getY()), cvPoint((int)circle.getX(), (int)circle.getY()), CvScalar.RED, 5, CV_AA, 0);
			
			double[] hsv2 = PixelOperations.getPixelHSV(croppedImage, (int)circle.getX(),(int)circle.getY()-sampleRange);
			colors.add(new HSVColor(hsv2[0], hsv2[1], hsv2[2]));
//			cvLine(croppedImage, cvPoint((int)circle.getX(), (int)circle.getY()), cvPoint((int)circle.getX(), (int)circle.getY()-sampleRange), CvScalar.RED, 5, CV_AA, 0);
			
			double[] hsv3 = PixelOperations.getPixelHSV(croppedImage, (int)circle.getX()-sampleRange,(int)circle.getY()-sampleRange);
			colors.add(new HSVColor(hsv3[0], hsv3[1], hsv3[2]));
//			cvLine(croppedImage, cvPoint((int)circle.getX(), (int)circle.getY()), cvPoint((int)circle.getX()-sampleRange, (int)circle.getY()-sampleRange), CvScalar.RED, 5, CV_AA, 0);
			
			double[] hsv4 = PixelOperations.getPixelHSV(croppedImage, (int)circle.getX()+sampleRange,(int)circle.getY()-sampleRange);
			colors.add(new HSVColor(hsv4[0], hsv4[1], hsv4[2]));
//			cvLine(croppedImage, cvPoint((int)circle.getX(), (int)circle.getY()), cvPoint((int)circle.getX()+sampleRange, (int)circle.getY()-sampleRange), CvScalar.RED, 5, CV_AA, 0);
			
			double[] hsv5 = PixelOperations.getPixelHSV(croppedImage, (int)circle.getX()-sampleRange,(int)circle.getY());
			colors.add(new HSVColor(hsv5[0], hsv5[1], hsv5[2]));
//			cvLine(croppedImage, cvPoint((int)circle.getX(), (int)circle.getY()), cvPoint((int)circle.getX()-sampleRange, (int)circle.getY()), CvScalar.RED, 5, CV_AA, 0);
			
			double[] hsv6 = PixelOperations.getPixelHSV(croppedImage, (int)circle.getX()+sampleRange,(int)circle.getY());
			colors.add(new HSVColor(hsv6[0], hsv6[1], hsv6[2]));
//			cvLine(croppedImage, cvPoint((int)circle.getX(), (int)circle.getY()), cvPoint((int)circle.getX()+sampleRange, (int)circle.getY()), CvScalar.RED, 5, CV_AA, 0);
			
			double[] hsv7 = PixelOperations.getPixelHSV(croppedImage, (int)circle.getX(),(int)circle.getY()+sampleRange);
			colors.add(new HSVColor(hsv7[0], hsv7[1], hsv7[2]));
//			cvLine(croppedImage, cvPoint((int)circle.getX(), (int)circle.getY()), cvPoint((int)circle.getX(), (int)circle.getY()+sampleRange), CvScalar.RED, 5, CV_AA, 0);
			
			double[] hsv8 = PixelOperations.getPixelHSV(croppedImage, (int)circle.getX()-sampleRange,(int)circle.getY()+sampleRange);
			colors.add(new HSVColor(hsv8[0], hsv8[1], hsv8[2]));
//			cvLine(croppedImage, cvPoint((int)circle.getX(), (int)circle.getY()), cvPoint((int)circle.getX()-sampleRange, (int)circle.getY()+sampleRange), CvScalar.RED, 5, CV_AA, 0);
			
			double[] hsv9 = PixelOperations.getPixelHSV(croppedImage, (int)circle.getX()+sampleRange,(int)circle.getY()+sampleRange);
			colors.add(new HSVColor(hsv9[0], hsv9[1], hsv9[2]));
//			cvLine(croppedImage, cvPoint((int)circle.getX(), (int)circle.getY()), cvPoint((int)circle.getX()+sampleRange, (int)circle.getY()+sampleRange), CvScalar.RED, 5, CV_AA, 0);
			
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
			
			System.out.println("H: " + Math.round(hue) + ", S: " + Math.round(saturation) + ", V: " + Math.round(brightness));
			
//			canvas.showImage(croppedImage);
		}
		
		System.out.println(" ----------- ");
		
		cvReleaseMemStorage(circles.storage());
		
		imageGray.release();
		croppedImage.release();
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
		
		colorA.setBackground(PixelOperations.getHSVColor(avgH,avgS,avgV));
		
//		canvas.showImage(image);
	}
	
	public static void main(String[] args) {
		new WebcamSettings().start();
	}
	
}
