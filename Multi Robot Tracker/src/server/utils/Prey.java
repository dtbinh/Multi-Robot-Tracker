package server.utils;

import commoninterface.mathutils.Vector2d;
import tracking.GroundPoint;

public class Prey {
	
	public Vector2d pp;
	public GroundPoint gp;
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Prey other = (Prey) obj;
		if (gp == null) {
			if (other.gp != null)
				return false;
		} else if (!gp.equals(other.gp))
			return false;
		if (pp == null) {
			if (other.pp != null)
				return false;
		} else if (!pp.equals(other.pp))
			return false;
		return true;
	}

}
