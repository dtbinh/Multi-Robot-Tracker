package server.utils;

import tracking.Vector2d;

/**
 * Converts a pixel coordinate to a real-world coordinate,
 * or vice-versa.
 * 
 * @author miguelduarte
 *
 */
public class Translator {
	
	public static GroundPoint[][] points = {
			{//Top
				new GroundPoint(86,120,0,120),
				new GroundPoint(243,125,30,120),
				new GroundPoint(400,131,60,120),
				new GroundPoint(557,137,90,120),
				new GroundPoint(713,140,120,120),
				new GroundPoint(868,144,150,120),
				new GroundPoint(1027,148,180,120),
				new GroundPoint(1183,150,210,120)
			},
			{//Right
				new GroundPoint(1170,778,210,0),
				new GroundPoint(1172,621,210,30),
				new GroundPoint(1173,464,210,60),
				new GroundPoint(1180,308,210,90),
				new GroundPoint(1183,150,210,120)
			},
			{//Bottom
				new GroundPoint(65,745,0,0),
				new GroundPoint(227,752,30,0),
				new GroundPoint(382,757,60,0),
				new GroundPoint(540,760,90,0),
				new GroundPoint(697,767,120,0),
				new GroundPoint(855, 772,150,0),
				new GroundPoint(1012,775,180,0),
				new GroundPoint(1183,150,210,0)
			},
			{//Left
				new GroundPoint(86,120,0,120),
				new GroundPoint(81,272,0,90),
				new GroundPoint(76,429,0,60),
				new GroundPoint(70,588,0,30),
				new GroundPoint(65,745,0,0)
			}
	};
	
	public static Vector2d getRealPosition(Vector2d point) {
		
		GroundPoint top = getClosestReal(points[0], point, true);
		GroundPoint right = getClosestReal(points[1], point, false);
		GroundPoint down = getClosestReal(points[2], point, true);
		GroundPoint left = getClosestReal(points[3], point, false);
		
		double minY = top.imageY;
		double maxY = down.imageY;
		
		double minX = left.imageX;
		double maxX = right.imageX;
		
		double diffX = maxX - minX;
		double diffY = maxY - minY;
		
		double percentX = (point.x-minX)/diffX;
		double percentY = (maxY-point.y)/diffY;
		
		return new Vector2d(right.groundX*percentX,top.groundY*percentY);
	}
	
	public static Vector2d getPixelPosition(Vector2d point) {
		
		GroundPoint top = getClosestPixel(points[0], point, true);
		GroundPoint right = getClosestPixel(points[1], point, false);
		GroundPoint down = getClosestPixel(points[2], point, true);
		GroundPoint left = getClosestPixel(points[3], point, false);
		
		double minY = top.groundY;
		double maxY = down.groundY;
		
		double minX = left.groundX;
		double maxX = right.groundX;
		
		double diffX = maxX - minX;
		double diffY = maxY - minY;
		
		double percentX = (point.x-minX)/diffX;
		double percentY = (point.y-minY)/diffY;
		
		double newX = percentX*(right.imageX-left.imageX)+left.imageX;
		double newY = percentY*(down.imageY-top.imageY)+top.imageY;
		
		return new Vector2d(newX,newY);
	}
	
	private static GroundPoint getClosestReal(GroundPoint[] points, Vector2d target, boolean x) {
		
		double min = Double.MAX_VALUE;
		GroundPoint closest = null;
		
		for(GroundPoint p : points) {
			double diff = x ? Math.abs(p.imageX-target.x) : Math.abs(p.imageY-target.y);
			if(diff < min) {
				min = diff;
				closest = p;
			}
		}
		
		return closest;
	}
	
	private static GroundPoint getClosestPixel(GroundPoint[] points, Vector2d target, boolean x) {
		
		double min = Double.MAX_VALUE;
		GroundPoint closest = null;
		
		for(GroundPoint p : points) {
			double diff = x ? Math.abs(p.groundX-target.x) : Math.abs(p.groundY-target.y);
			if(diff < min) {
				min = diff;
				closest = p;
			}
		}
		
		return closest;
	}

}
