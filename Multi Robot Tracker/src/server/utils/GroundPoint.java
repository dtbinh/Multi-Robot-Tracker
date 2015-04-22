package server.utils;

public class GroundPoint {
	
	double imageX = 0;
	double imageY = 0;
	double groundX = 0;
	double groundY = 0;
	
	public GroundPoint(double ix,double iy,double gx,double gy) {
		imageX = ix;
		imageY = iy;
		groundX = gx;
		groundY = gy;
	}

	public double getImageX() {
		return imageX;
	}

	public double getImageY() {
		return imageY;
	}

	public double getGroundX() {
		return groundX;
	}

	public double getGroundY() {
		return groundY;
	}
	
}
