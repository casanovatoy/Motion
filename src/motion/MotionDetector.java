package motion;
// MotionDetector.java
// Andrew Davison, July 2013, ad@fivedots.psu.ac.th

/* Show a sequence of images snapped from a webcam in a picture panel (MotionPanel). 
 Any change between the current frame and the previous is detected using JavaCV and
 crosshairs are drawn at the center-of-gravity (COG) of the difference.

 Usage:
 > java MotionDetector
 */
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class MotionDetector extends JFrame {

    // GUI components

    private MotionPanel motionPanel;

    public MotionDetector() {
        super("Motion Detector");

        Container c = getContentPane();
        c.setLayout(new BorderLayout());

        motionPanel = new MotionPanel(); // the sequence of pictures appear here
        c.add(motionPanel, BorderLayout.CENTER);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                motionPanel.closeDown();    // stop snapping pics
                System.exit(0);
            }
        });

        setResizable(false);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    } // end of MotionDetector()

  // -------------------------------------------------------
    public static void main(String args[]) {
        new MotionDetector();
    }

} // end of MotionDetector class
