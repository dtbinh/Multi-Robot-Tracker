package server;

import static com.googlecode.javacv.cpp.opencv_core.CV_AA;
import static com.googlecode.javacv.cpp.opencv_core.cvLine;
import static com.googlecode.javacv.cpp.opencv_core.cvPoint;

import java.util.LinkedList;

import server.utils.GroundPoint;
import server.utils.Translator;
import tracking.Vector2d;

import com.googlecode.javacv.cpp.opencv_core.CvScalar;
import com.googlecode.javacv.cpp.opencv_core.IplImage;

public class ServerEnvironment {
	
	public LinkedList<GroundPoint> objects = new LinkedList<GroundPoint>();
	private static int WIDTH = 10;
	
	public void addObjectCoordinates(Vector2d p) {
		Vector2d pixelPos = Translator.getPixelPosition(p);
		objects.add(new GroundPoint(pixelPos.x,pixelPos.y,p.x,p.y));
	}
	
	public void drawObjects(IplImage img) {
		for(GroundPoint p : objects) {
			cvLine(img, cvPoint((int)p.getImageX(), (int)p.getImageY()), cvPoint((int)p.getImageX(), (int)p.getImageY()), CvScalar.RED, WIDTH, CV_AA, 0);
		}
	}
	
	public void removeObject(Vector2d object) {	
		for(int i = 0 ; i < objects.size() ; i++) {
			if(object.x == objects.get(i).getGroundX() && object.y == objects.get(i).getGroundY()) {
				objects.remove(i);
			}
		}
	}

	public void removeAllObjects() {
		objects.clear();
	}

}