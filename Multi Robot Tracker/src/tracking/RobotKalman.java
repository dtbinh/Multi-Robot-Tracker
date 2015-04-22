package tracking;
import static com.googlecode.javacv.cpp.opencv_core.cvPoint;
import jama.Matrix;
import jkalman.JKalman;

import com.googlecode.javacv.cpp.opencv_core.CvPoint;

/**
 * A simple Kalman Filter with a linear model. It should
 * decrease the noise of the readings from the video samples.
 * 
 * @author miguelduarte
 */
public class RobotKalman {

	JKalman kalman;
	Matrix s; // state [x, y]
	Matrix c; // corrected state [x, y]
	Matrix m; // measurement [x]
	boolean configured = false;

	public void configure(CvPoint coord1, CvPoint coord2, CvPoint coord3) {
		
		try {

			kalman = new JKalman(12, 6);

			s = new Matrix(12, 1);
			c = new Matrix(12, 1);

			m = new Matrix(6, 1);
			m.set(0, 0, coord1.x());
			m.set(1, 0, coord1.y());
			
			m.set(2, 0, coord2.x());
			m.set(3, 0, coord2.y());
			
			m.set(4, 0, coord3.x());
			m.set(5, 0, coord3.y());
			
			// transitions for x, y
            double[][] tr = { {1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0}, //   p1x
                              {0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0}, //p1y
                              {0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0}, //p2x
                              {0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0}, //p2y
                              {0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0}, //p3x
                              {0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1}, //p3y
                              {0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0}, //   v1x
                              {0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0}, //v1y
                              {0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0},
                              {0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0},
                              {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0},
                              {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1},};
            
            kalman.setTransition_matrix(new Matrix(tr));
            
            kalman.setError_cov_post(kalman.getError_cov_post().identity());

		} catch (Exception e) {
			e.printStackTrace();
		}
		
		configured = true;
		
	}
	
	public CvPoint[] getEstimation(CvPoint coord1, CvPoint coord2, CvPoint coord3) {
		
		if(!configured)
			configure(coord1,coord2,coord3);
		
		s = kalman.Predict();
		
		m.set(0, 0, coord1.x());
        m.set(1, 0, coord1.y());
        m.set(2, 0, coord2.x());
        m.set(3, 0, coord2.y());
        m.set(4, 0, coord3.x());
        m.set(5, 0, coord3.y());
        
        c = kalman.Correct(m);
        
        CvPoint p1 = cvPoint((int)c.get(0, 0),(int)c.get(1, 0));
        CvPoint p2 = cvPoint((int)c.get(2, 0),(int)c.get(3, 0));
        CvPoint p3 = cvPoint((int)c.get(4, 0),(int)c.get(5, 0));
        
        return new CvPoint[]{p1,p2,p3};
		
	}

}
