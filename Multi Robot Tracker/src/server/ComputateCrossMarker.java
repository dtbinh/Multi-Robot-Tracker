package server;

import static com.googlecode.javacv.cpp.opencv_core.*;
import static com.googlecode.javacv.cpp.opencv_imgproc.*;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.text.DecimalFormat;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.Border;

import server.utils.CircleMarker;
import server.utils.PixelOperations;
import tracking.RobotKalman;

import com.googlecode.javacpp.Pointer;
import com.googlecode.javacv.CanvasFrame;
import com.googlecode.javacv.cpp.opencv_core.CvMemStorage;
import com.googlecode.javacv.cpp.opencv_core.CvPoint;
import com.googlecode.javacv.cpp.opencv_core.CvRect;
import com.googlecode.javacv.cpp.opencv_core.CvScalar;
import com.googlecode.javacv.cpp.opencv_core.CvSeq;
import com.googlecode.javacv.cpp.opencv_core.IplImage;
import commoninterface.mathutils.Vector2d;

public class ComputateCrossMarker extends Thread {

	public CvPoint robotPosition = cvPoint(0,0);
	public double robotOrientation = 0;
	public int successAngle = 0;
	
	private CanvasFrame frame;
	private JLabel informationLabel;
	private JTextField colorTextfield;
	private Border border;
	private Color color;
	
	private IplImage image;
	private CircleMarker markerCircle;
	
	private RobotKalman kalman = new RobotKalman();
	private CvPoint intersection = cvPoint(0,0);
	private CvPoint tail = cvPoint(0,0);
    private CvPoint center = cvPoint(0,0);
	
	private CvPoint estTail = cvPoint(0,0);
	private CvPoint estIntersection = cvPoint(0,0);
    private CvPoint estCenter = cvPoint(0,0);
	private CvRect roi;
	private boolean successfulIntersection = false;
	
	private Video video;
	private boolean working = false;
	private String threadName;
	private DecimalFormat df = new DecimalFormat("#.00");
	
	public ComputateCrossMarker(Video video, String threadName) {
		this.video = video;
		this.threadName = threadName;
		roi = null;
		frame = new CanvasFrame("Robot");
		JPanel panel = new JPanel(new BorderLayout());
		
		informationLabel = new JLabel(" ");
		colorTextfield = new JTextField(2);
		border = BorderFactory.createLineBorder(Color.BLACK);
		colorTextfield.setBorder(BorderFactory.createCompoundBorder(border,BorderFactory.createEmptyBorder(10, 10, 10, 10)));
		colorTextfield.setEditable(false);
		colorTextfield.setBackground(Color.BLACK);
		panel.add(colorTextfield, BorderLayout.WEST);
		panel.add(informationLabel);
		
		frame.getContentPane().add(panel,BorderLayout.SOUTH);
		frame.setPreferredSize(new Dimension(600, 400));
		frame.setLocation(1249, 0);
	}
	
	@Override
	public void run() {
		frame.setTitle(Thread.currentThread().getName());
		Thread.currentThread().setName(threadName);
		
		try {
			while (true){
				processMarker();
			}
		} catch (InterruptedException e) {
			System.out.println(currentThread().getName() + " was interruped and is going to die!");
		}
		frame.dispose();
	}

	private void processMarker() throws InterruptedException {
//		System.out.println("Let's Go!");
		while(!working){
			synchronized (this) {
//				System.out.println(Thread.currentThread().getName() + " is waiting ...");
				wait();
			}
		}
		working = false;
		
		float x=markerCircle.getX();
		float y=markerCircle.getY();
		
//		if(successfulIntersection) {
//			x+=center.x()-Video.widthRoi/2;
//			y+=center.y()-Video.widthRoi/2;
//		}
		
		center = cvPoint((int)x,(int)y);
		
		int width = Math.round(markerCircle.getZ())*2;
		
		//Crop image
		int roiX = (int)x-width/2;
		int roiY = (int)y-width/2;
		
		roi = cvRect(roiX, roiY, width, width);
		
		if(roi.width() >= 0 && roi.height() >= 0 
				&& roi.x() < image.width() && roi.y() < image.height() 
				&& (roi.x() + width) >= roi.width() 
				&& roi.y() + width >= roi.height()){
			
			cvSetImageROI(image,roi);
			IplImage tempImage = IplImage.create( cvSize(roi.width(), roi.height()), 8, 3);
			IplImage maskedImage = IplImage.create(cvSize(roi.width(), roi.height()), 8, 3);
			IplImage roiImg = IplImage.create( cvSize(roi.width(),roi.width()), 8, 1);
			
			cvCopy(image,tempImage);
			
			cvResetImageROI(image);
			
		    // prepare the 'ROI' image
		    cvZero(roiImg);
		    
		    cvCircle(
		        roiImg,
		        cvPoint(width/2, width/2),
		        (int)(markerCircle.getZ()),
		        CV_RGB(255, 255, 255),
		        -1, 8, 0
		    );
		    
		    cvZero(maskedImage);

		    // extract subimage
			cvNot(tempImage,tempImage);
		    cvCopy(tempImage, maskedImage, roiImg);
//		    showImage(maskedImage);

			IplImage thresholdedCross = IplImage.create(maskedImage.cvSize(),8,1);
			
			cvCvtColor(maskedImage, thresholdedCross, CV_BGR2GRAY);
			
			int[] rgb = PixelOperations.getPixelRGB(image, (int)markerCircle.getX(), (int)markerCircle.getY());
//			System.out.println("R: " + rgb[0] +" G: "+ rgb[1] + " B: " + rgb[2]);
			int avgRGB = (int)((rgb[0] + rgb[1] + rgb[2])/3/2);
			
			//Extract cross
			cvThreshold(thresholdedCross, thresholdedCross, 120, 255, CV_THRESH_BINARY);
//			cvSmooth(thresholdedCross, thresholdedCross, CV_GAUSSIAN, 3);
			
//			showImage(thresholdedCross);
			
			int erodeValue = 4;
			boolean success = false;
			IplImage thresholdedCrossCopy = IplImage.create(thresholdedCross.cvSize(),8,1);
			
			if(thresholdedCross.width() > 0 && thresholdedCross.height() > 0){
				while (!success && erodeValue >= 0) {
					
					cvCopy(thresholdedCross, thresholdedCrossCopy);
					
					cvErode(thresholdedCrossCopy, thresholdedCrossCopy, null, erodeValue);
					
					CvMemStorage storage2 = cvCreateMemStorage(0);
					
					CvSeq lines = cvHoughLines2(thresholdedCrossCopy, storage2, CV_HOUGH_PROBABILISTIC, 1, Math.PI / 180, 20, 15, 50); // 20,15,50
					int[] perpendicularLinesIndexes = getPerpendicularLines(lines);
					
					if(perpendicularLinesIndexes[1] != 0) {
						
						CvPoint inter =
							calculateIntersectionPoint(
								cvGetSeqElem(lines,perpendicularLinesIndexes[0]),
								cvGetSeqElem(lines,perpendicularLinesIndexes[1])
							);
						
						successfulIntersection = false;
						
						if(inter != null) {
							
							successfulIntersection = true;
							success = true;
							successAngle++; 
							
							intersection = inter;
							
							CvPoint pt1  = new CvPoint(cvGetSeqElem(lines,perpendicularLinesIndexes[0])).position(0);
					    	CvPoint pt2  = new CvPoint(cvGetSeqElem(lines,perpendicularLinesIndexes[0])).position(1);
					    	CvPoint pt3  = new CvPoint(cvGetSeqElem(lines,perpendicularLinesIndexes[1])).position(0);
					        CvPoint pt4  = new CvPoint(cvGetSeqElem(lines,perpendicularLinesIndexes[1])).position(1);
					        
					        cvLine(thresholdedCross, pt1, pt2, CvScalar.BLACK, 3, CV_AA, 0);
					        cvLine(thresholdedCross, pt3, pt4, CvScalar.BLACK, 3, CV_AA, 0);
					        
					        showImage(thresholdedCross);
					        
					        CvPoint midpoint1 = cvPoint((pt1.x()+pt2.x())/2, (pt1.y()+pt2.y())/2); 
					        CvPoint midpoint2 = cvPoint((pt3.x()+pt4.x())/2, (pt3.y()+pt4.y())/2);
					        tail = null;
					        
					        if(distanceBetween(intersection,midpoint1) > distanceBetween(intersection,midpoint2)) {
					        	
					        	if(distanceBetween(pt1,intersection) > distanceBetween(pt2, intersection))
					        		tail = pt1;
					        	else
					        		tail = pt2;
					        } else {
					        	if(distanceBetween(pt3,intersection) > distanceBetween(pt4, intersection))
					        		tail = pt3;
					        	else
					        		tail = pt4;
					        }
					        
					        tail = cvPoint((int)(tail.x()+(x-width/2)),(int)(tail.y()+(y-width/2)));
						    intersection = cvPoint((int)(intersection.x()+(x-width/2)),(int)(intersection.y()+(y-width/2)));
						    CvPoint[] estimation = kalman.getEstimation(tail,intersection,center);
						    estTail = estimation[0];
						    estIntersection = estimation[1];
						    estCenter = estimation[2];
						    
						    robotPosition = cvPoint(estCenter.x(),estCenter.y());
						    
						}
					}else
						successfulIntersection = false;
					
					erodeValue--;
					cvReleaseMemStorage(lines.storage());
				}
			}else
		    	successfulIntersection = false;
			
			roiImg.release();
			tempImage.release();
			maskedImage.release();
			thresholdedCross.release();
		}else{
			successfulIntersection = false;
		}
		
		if(!successfulIntersection)
			robotPosition = cvPoint(center.x(),center.y());
		
		
		double orientation = cvFastArctan(estIntersection.y()-estTail.y(),estIntersection.x()-estTail.x());
		robotOrientation = (orientation-360)*-1;
		
		image.release();
		video.incrementThreadsDone();
	}
	
	private double distanceBetween(CvPoint p1, CvPoint p2) {
		return Math.sqrt(Math.pow(p1.x()-p2.x(),2)+Math.pow(p1.y()-p2.y(),2));
	}
	
	private int[] getPerpendicularLines(CvSeq lines) {
		
		double[] angles = new double[2];
		int[] indexes = new int[2];
        int angleIndex = 0;
        
        for(int j = 0 ; j < lines.total() && angleIndex < 2 ; j++) {
        	angles = new double[2];
        	indexes = new int[2];
        	angleIndex = 0;
        	
	        for (int i = j; i < lines.total(); i++) {
	
	            Pointer line = cvGetSeqElem(lines, i);
	            CvPoint pt1  = new CvPoint(line).position(0);
	            CvPoint pt2  = new CvPoint(line).position(1);
	            
	            double angle = cvFastArctan( pt2.y()-pt1.y(),pt2.x()- pt1.x());
	            
	            while(angle < 0)
	            	angle+=360;
	            
	            while(angle > 180)
	            	angle-=180;
	            
	            if(angleIndex == 0) {
	            	indexes[angleIndex] = i;
	            	angles[angleIndex++] = angle;
	            } else if(angleIndex == 1){
	            	
	            	double minAngle = Math.min(angles[0], angle);
	            	double maxAngle = Math.max(angles[0], angle);
	            	
	            	if(maxAngle - 90 < minAngle + 5 && maxAngle -90 > minAngle - 5) {
	            		indexes[angleIndex] = i;
	            		angles[angleIndex++] = angle;
	            		break;
	            	}
	            	
	            }
	        }
        }
        return indexes;		
	}
	
	private CvPoint calculateIntersectionPoint(Pointer line1, Pointer line2) {
		
        CvPoint p1  = new CvPoint(line1).position(0);
        CvPoint p2  = new CvPoint(line1).position(1);
        
        CvPoint p3  = new CvPoint(line2).position(0);
        CvPoint p4  = new CvPoint(line2).position(1);
		
		double x1 = p1.x(), x2 = p2.x(), x3 = p3.x(), x4 = p4.x();
		double y1 = p1.y(), y2 = p2.y(), y3 = p3.y(), y4 = p4.y();
		double d = (x1-x2)*(y3-y4) - (y1-y2)*(x3-x4);
		if (d == 0) 
			return null;
		double xi = ((x3-x4)*(x1*y2-y1*x2)-(x1-x2)*(x3*y4-y3*x4))/d;
		double yi = ((y3-y4)*(x1*y2-y1*x2)-(y1-y2)*(x3*y4-y3*x4))/d;
		
		return cvPoint((int)xi,(int)yi);
	}
	
	private void showImage(IplImage img) {
		if(frame != null)
			frame.showImage(img);
	}
	
	public synchronized void setParameters(IplImage image, CircleMarker markerCircle){
		this.image = image;
		this.markerCircle = markerCircle;
//		System.out.println(Thread.currentThread().getName() + " notified " + this.getName() + "!");
		working = true;
		notifyAll();
	}
	
	public boolean isSuccessfulIntersection() {
		return successfulIntersection;
	}
	
	public CvPoint getEstTail() {
		return estTail;
	}
	
	public CvPoint getEstIntersection() {
		return estIntersection;
	}
	
	public CvPoint getEstCenter() {
		return estCenter;
	}
	
	public Color getColor() {
		return color;
	}
	
	public void setColor(Color color) {
		this.color = color;
	}
	
	public void setLabelText(Color color, Integer robotID, Vector2d position, double orientation, boolean detectingIntersection){
		colorTextfield.setBackground(color);
		informationLabel.setText("Robot ID: " + robotID + " - Position = (" + df.format(position.x) + ", " + df.format(position.y) + ") - Orientation: " + df.format(orientation) + " - Detecting: " + detectingIntersection);
	}
	
}