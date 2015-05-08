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
	
	public int imageX = 0;
	public int imageY = 0;
	public int groundX = 0;
	public int groundY = 0;
	
	public GroundPoint(int ix,int iy,int gx,int gy) {
		imageX = ix;
		imageY = iy;
		groundX = gx;
		groundY = gy;
	}

}
