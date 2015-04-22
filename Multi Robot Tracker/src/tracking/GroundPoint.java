package tracking;

/**
 * A GroundPoint has both the coordinates of the image, and it's
 * correspondent "real world" coordinate. This is necessary since the
 * view of the camera does not accurately represent the real world, due
 * to perspective, aperture and camera lens issues. 
 * 
 * @author miguelduarte
 */
public class GroundPoint {
	
	int imageX = 0;
	int imageY = 0;
	int groundX = 0;
	int groundY = 0;
	
	public GroundPoint(int ix,int iy,int gx,int gy) {
		imageX = ix;
		imageY = iy;
		groundX = gx;
		groundY = gy;
	}

}
