package server.utils;


public class HSVColor implements Comparable<HSVColor> {

	private double hue;
	private double saturation;
	private double brightness;
	
	public HSVColor(double hue, double saturation, double brightness) {
		this.hue = hue;
		this.saturation = saturation;
		this.brightness = brightness;
	}
	
	public double getHue() {
		return hue;
	}
	
	public double getSaturation() {
		return saturation;
	}
	
	public double getBrightness() {
		return brightness;
	}
	
	@Override
	public String toString() {
		return "[ " + hue + ", " + saturation + ", " + brightness + " ]";
	}

	@Override
	public int compareTo(HSVColor o) {
		if(this.getBrightness() - o.getBrightness() > 0){
			return 1;
		}else if(this.getBrightness() - o.getBrightness() < 0){
			return -1;
		}else
			return 0;
	}
	
}
