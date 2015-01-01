package motion;
// MotionPanel.java
// Andrew Davison, July 2013, ad@fivedots.psu.ac.th

/* This panel repeatedly snaps a picture and draw it onto
 the panel. OpenCV is used, via the JCVMotionDetector class, to detect
 movement.

 A crosshairs graphic is drawn onto the image, positioned at the
 center-of-gravity (COG) of the motion.
 */
import java.awt.*;
import javax.swing.*;
import java.awt.image.*;
import java.io.*;
import javax.imageio.*;

//import com.googlecode.javacv.*;
//import com.googlecode.javacv.cpp.*;
//import com.googlecode.javacv.cpp.videoInputLib.*;
//
//import static com.googlecode.javacv.cpp.opencv_core.*;
//import static com.googlecode.javacv.cpp.opencv_imgproc.*;
//import static com.googlecode.javacv.cpp.opencv_highgui.*;
//import static com.googlecode.javacv.cpp.avutil.*;   // for grabber/recorder constants
import org.bytedeco.javacv.*;
import org.bytedeco.javacpp.videoInputLib.*;
import java.awt.Point;

import static org.bytedeco.javacpp.opencv_core.*;

public class MotionPanel extends JPanel implements Runnable {
    /* dimensions of each image; the panel is the same size as the image */

    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;

    private static final int DELAY = 100;  // time (ms) between redraws of the panel

    private static final String CROSSHAIRS_FNM = "crosshairs.png";

    private static final int MIN_MOVE_REPORT = 3;    // for reporting a move

    private static final int CAMERA_ID = 0;

    private IplImage snapIm = null;  // current webcam snap
    private volatile boolean isRunning;
    private volatile boolean isFinished;

    // used for the average ms snap time information
    private int imageCount = 0;
    private long totalTime = 0;
    private Font msgFont;

    private Point prevCogPoint = null; // holds the coordinates of the motion COG
    private Point cogPoint = null;
    private BufferedImage crosshairs;

    public MotionPanel() {
        setBackground(Color.white);
        msgFont = new Font("SansSerif", Font.BOLD, 18);

        // load the crosshairs image (a transparent PNG)
        crosshairs = loadImage(CROSSHAIRS_FNM);

        new Thread(this).start();   // start updating the panel's image
    } // end of MotionPanel()

    private BufferedImage loadImage(String imFnm) // return an image
    {
        BufferedImage image = null;
        try {
            image = ImageIO.read(new File(imFnm));   // read in as an image
            System.out.println("Reading image " + imFnm);
        } catch (Exception e) {
            System.out.println("Could not read image from " + imFnm);
        }
        return image;
    }  // end of loadImage()

    public Dimension getPreferredSize() // make the panel wide enough for an image
    {
        return new Dimension(WIDTH, HEIGHT);
    }

    public void run() /* display the current webcam image every DELAY ms
     The time statistics gathered here include the time taken to
     detect movement.
     */ {
        FrameGrabber grabber = initGrabber(CAMERA_ID);
        if (grabber == null) {
            return;
        }

        snapIm = picGrab(grabber, CAMERA_ID);
        JCVMotionDetector md = new JCVMotionDetector(snapIm);

        Point pt;
        long duration;
        isRunning = true;
        isFinished = false;

        while (isRunning) {
            long startTime = System.currentTimeMillis();

            snapIm = picGrab(grabber, CAMERA_ID);

            md.calcMove(snapIm);    // update detector with new image
            if ((pt = md.getCOG()) != null) {    // get new COG
                prevCogPoint = cogPoint;
                cogPoint = pt;
                reportCOGChanges(cogPoint, prevCogPoint);
            }

            imageCount++;
            repaint();

            duration = System.currentTimeMillis() - startTime;
            totalTime += duration;
            if (duration < DELAY) {
                try {
                    Thread.sleep(DELAY - duration);  // wait until DELAY time has passed
                } catch (Exception ex) {
                }
            }
        }
        closeGrabber(grabber, CAMERA_ID);
        System.out.println("Execution terminated");
        isFinished = true;
    }  // end of run()

    private FrameGrabber initGrabber(int ID) {
        FrameGrabber grabber = null;
        System.out.println("Initializing grabber for " + videoInput.getDeviceName(ID) + " ...");
        try {
            grabber = FrameGrabber.createDefault(ID);
            grabber.setFormat("dshow");       // using DirectShow
            grabber.setImageWidth(WIDTH);     // default is too small: 320x240
            grabber.setImageHeight(HEIGHT);
            grabber.start();
        } catch (Exception e) {
            System.out.println("Could not start grabber");
            System.out.println(e);
            System.exit(1);
        }
        return grabber;
    }  // end of initGrabber()

    private IplImage picGrab(FrameGrabber grabber, int ID) {
        IplImage im = null;
        try {
            im = grabber.grab();  // take a snap
        } catch (Exception e) {
            System.out.println("Problem grabbing image for camera " + ID);
        }
        return im;
    }  // end of picGrab()

    private void closeGrabber(FrameGrabber grabber, int ID) {
        try {
            grabber.stop();
            grabber.release();
        } catch (Exception e) {
            System.out.println("Problem stopping grabbing for camera " + ID);
        }
    }  // end of closeGrabber()

    private void reportCOGChanges(Point cogPoint, Point prevCogPoint) // compare cogPoint and prevCogPoint
    {
        if (prevCogPoint == null) {
            return;
        }

        // calculate the distance moved and the angle (in degrees)
        int xStep = cogPoint.x - prevCogPoint.x;
        int yStep = -1 * (cogPoint.y - prevCogPoint.y);      // so + y-axis is up screen

        int distMoved = (int) Math.round(Math.sqrt((xStep * xStep) + (yStep * yStep)));
        int angle = (int) Math.round(Math.toDegrees(Math.atan2(yStep, xStep)));

        if (distMoved > MIN_MOVE_REPORT) {
            System.out.println("COG: (" + cogPoint.x + ", " + cogPoint.y + ")");
            System.out.println("  Dist moved: " + distMoved + "; angle: " + angle);
        }
    }  // end of reportCOGChanges()

    public void paintComponent(Graphics g) /* Draw the image, the movement crosshairs, and the 
     average ms snap time at the bottom left of the panel. 
     */ {
        super.paintComponent(g);

        g.setFont(msgFont);

        // draw the image, crosshairs, and stats 
        if (snapIm != null) {
            g.drawImage(snapIm.getBufferedImage(), 0, 0, this);   // draw the snap

            if (cogPoint != null) {
                drawCrosshairs(g, cogPoint.x, cogPoint.y);   // positioned at COG
            }
            g.setColor(Color.YELLOW);
            String statsMsg = String.format("Snap Avg. Time:  %.1f ms",
                    ((double) totalTime / imageCount));
            g.drawString(statsMsg, 5, HEIGHT - 10);
            // write statistics in bottom-left corner
        } else {// no image yet
            g.setColor(Color.BLUE);
            g.drawString("Loading from camera " + CAMERA_ID + "...", 5, HEIGHT - 10);
        }
    } // end of paintComponent()

    private void drawCrosshairs(Graphics g, int xCenter, int yCenter) // draw crosshairs graphic or a red circle
    {
        if (crosshairs != null) {
            g.drawImage(crosshairs, xCenter - crosshairs.getWidth() / 2,
                    yCenter - crosshairs.getHeight() / 2, this);
        } else {
            g.setColor(Color.RED);
            g.fillOval(xCenter - 10, yCenter - 10, 20, 20);
        }
    }  // end of drawCrosshairs()

  // --------------- called from the top-level JFrame ------------------
    public void closeDown() /* Terminate run() and wait for it to finish.
     This stops the application from exiting until everything
     has finished. */ {
        isRunning = false;
        while (!isFinished) {
            try {
                Thread.sleep(DELAY);
            } catch (Exception ex) {
            }
        }
    } // end of closeDown()

} // end of MotionPanel class

