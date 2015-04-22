package server.utils;

public class CircleMarker {

	private float x;
	private float y;
	private float z;
	
	public CircleMarker(float f, float g, float h) {
		this.x = f;
		this.y = g;
		this.z = h;
	}
	
	public float getX() {
		return x;
	}
	
	public float getY() {
		return y;
	}
	
	public float getZ() {
		return z;
	}
	
	public double calculateDistance(CircleMarker oldCircle){
		return Math.sqrt((Math.pow((this.getX() - oldCircle.getX()), 2) + Math.pow((this.getY()-oldCircle.getY()), 2)));
	}
	
}
