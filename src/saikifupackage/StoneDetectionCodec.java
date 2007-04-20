/*
 * StoneDetectionCodec.java
 * 
 * Beer-ware
 * Copyright (c) Lars Englund
 *
 * Kudos to Mattias Hedlund, Fredrik Jonsson and David Åberg at 
 * Luleå University of Technology for the basic JMF code!
 */

package saikifupackage;

import javax.media.*;
import javax.media.format.*;
import javax.media.util.*;
import java.awt.*;
import java.awt.image.*;
import java.io.IOException;
import java.util.*;


public class StoneDetectionCodec implements Effect {

    SaiKifuJFrame mainptr;    // Lite fulhack - gör SaiKifuJFrame till singleton istället, eller använd model-view-modellen
    
    private Vector clickedPoints = new Vector();
    private Vector stones = new Vector();
    private Vector gobanPoints = new Vector();
    private byte[] referenceImage = null;
    private boolean updateReference = true;
    private int threshold = 50;
    Timer timer = new Timer();
    boolean stoneTimerRunning = false;
    boolean stoneOk = false;
    
    
    class Stone {
        public double img_x;
        public double img_y;
        public int board_x;
        public int board_y;
        public int idx;
        
        public Stone(double x, double y) {
            img_x = x;
            img_y = y;
        }
        
        public Stone(double img_x, double img_y, int x, int y) {
            this.img_x = img_x;
            this.img_y = img_y;
            board_x = x;
            board_y = y;
        }
    }
    
    
    boolean gobanCreated = false;
    
    Point p1;
    Point p2;
    Point p3;
    Point p4;
    Point[] edge1 = new Point[19];
    Point[] edge2 = new Point[19];
    Point[] edge3 = new Point[19];
    Point[] edge4 = new Point[19];
    
   
    private Format inputFormat;
    private Format outputFormat;
    private Format[] inputFormats;
    private Format[] outputFormats;
    /**
     * The RGBFormat of the inbuffer.
     */
    private RGBFormat vfIn = null;
    /**
     * Image property.
     */
    private int imageWidth = 0;
    /**
     * Image property.
     */
    private int imageHeight = 0;
    /**
     * Image property.
     */
    private int imageArea = 0;
    
    /**
     * Initialize the effect plugin.
     */
    public StoneDetectionCodec() {
        inputFormats = new Format[] {
            new RGBFormat(null,
                    Format.NOT_SPECIFIED,
                    Format.byteArray,
                    Format.NOT_SPECIFIED,
                    24,
                    3, 2, 1,
                    3, Format.NOT_SPECIFIED,
                    Format.TRUE,
                    Format.NOT_SPECIFIED)
        };
        
        outputFormats = new Format[] {
            new RGBFormat(null,
                    Format.NOT_SPECIFIED,
                    Format.byteArray,
                    Format.NOT_SPECIFIED,
                    24,
                    3, 2, 1,
                    3, Format.NOT_SPECIFIED,
                    Format.FALSE,
                    Format.NOT_SPECIFIED)
        };
    }
    
    /**
     * Get the inputformats that we support.
     * @return  All supported Formats.
     */
    public Format[] getSupportedInputFormats() {
        return inputFormats;
    }
    
    /**
     * Get the outputformats that we support.
     * @param input the current inputformat.
     * @return  All supported Formats.
     */
    public Format [] getSupportedOutputFormats(Format input) {
        if (input == null) {
            return outputFormats;
        }
        if (matches(input, inputFormats) != null) {
            return new Format[] { outputFormats[0].intersects(input) };
        } else {
            return new Format[0];
        }
    }
    
    /**
     * Set the input format.
     *
     */
    public Format setInputFormat(Format input) {
        inputFormat = input;
        return input;
    }
    
    /**
     * Set our output format.
     *
     */
    public Format setOutputFormat(Format output) {
        
        if (output == null || matches(output, outputFormats) == null)
            return null;
        
        RGBFormat incoming = (RGBFormat) output;
        
        Dimension size = incoming.getSize();
        int maxDataLength = incoming.getMaxDataLength();
        int lineStride = incoming.getLineStride();
        float frameRate = incoming.getFrameRate();
        int flipped = incoming.getFlipped();
        int endian = incoming.getEndian();
        
        if (size == null)
            return null;
        if (maxDataLength < size.width * size.height * 3)
            maxDataLength = size.width * size.height * 3;
        if (lineStride < size.width * 3)
            lineStride = size.width * 3;
        if (flipped != Format.FALSE)
            flipped = Format.FALSE;
        
        outputFormat = outputFormats[0].intersects(new RGBFormat(size,
                maxDataLength,
                null,
                frameRate,
                Format.NOT_SPECIFIED,
                Format.NOT_SPECIFIED,
                Format.NOT_SPECIFIED,
                Format.NOT_SPECIFIED,
                Format.NOT_SPECIFIED,
                lineStride,
                Format.NOT_SPECIFIED,
                Format.NOT_SPECIFIED));
        
        return outputFormat;
    }
    
    
    
    
    /**
     * Process the buffer. This is where motion is analysed and optionally visualized.
     *
     */
    
    public synchronized int process(Buffer inBuffer, Buffer outBuffer) {
        int outputDataLength = ((VideoFormat)outputFormat).getMaxDataLength();
        validateByteArraySize(outBuffer, outputDataLength);
        outBuffer.setLength(outputDataLength);
        outBuffer.setFormat(outputFormat);
        outBuffer.setFlags(inBuffer.getFlags());
        
        //System.out.println("process");
        //System.out.println("in format: " + ((VideoFormat)inputFormat).toString());
        //System.out.println("out format: " + ((VideoFormat)outputFormat).toString());
        //System.out.println("outputDataLength: " + outputDataLength);
        //System.out.println("inBuffer.length: " + inBuffer.getLength());
        //System.out.println("outBuffer.length: " + outBuffer.getLength());
        
        byte [] inData = (byte[]) inBuffer.getData();
        byte [] outData =(byte[]) outBuffer.getData();
        int[] sqAvg = null;
        int[] refsqAvg = null;
        
        vfIn = (RGBFormat) inBuffer.getFormat();
        Dimension sizeIn = vfIn.getSize();
        
        int pixStrideIn = vfIn.getPixelStride();
        int lineStrideIn = vfIn.getLineStride();
        
        imageWidth = (vfIn.getLineStride())/3; //Divide by 3 since each pixel has 3 colours.
        imageHeight = ((vfIn.getMaxDataLength())/3)/imageWidth;
        imageArea = imageWidth*imageHeight;
        
        int r,g,b = 0; //Red, green and blue values.
                
        //Copy all data from the inbuffer to the outbuffer. The purpose is to display the video input on the screen.
        System.arraycopy(inData,0,outData,0,outData.length);
        
        if (updateReference) {
            referenceImage = new byte[outData.length];
            System.arraycopy(inData,0,referenceImage,0,outData.length);
            updateReference = false;
        }
        
        
        // Find bounding rect for changed area
        int min_x = imageWidth;
        int min_y = imageHeight;
        int max_x = 0;
        int max_y = 0;
        
        for (int y=0; y<imageHeight; y++) {
            for (int x=0; x<imageWidth; x++) {
                int bw1 = 0;
                r = (int) inData[y*imageWidth*3 + x*3 + 0] & 0xFF;
                g = (int) inData[y*imageWidth*3 + x*3 + 1] & 0xFF;
                b = (int) inData[y*imageWidth*3 + x*3 + 2] & 0xFF;
                bw1 = (int) ((r + b + g)/ (double) 3);
                
                int bw2 = 0;
                r = (int) referenceImage[y*imageWidth*3 + x*3 + 0] & 0xFF;
                g = (int) referenceImage[y*imageWidth*3 + x*3 + 1] & 0xFF;
                b = (int) referenceImage[y*imageWidth*3 + x*3 + 2] & 0xFF;
                bw2 = (int) ((r + b + g)/ (double) 3);
                
                if (Math.abs(bw1-bw2) > threshold) {
                    if (x < min_x)
                        min_x = x;
                    if (x > max_x)
                        max_x = x;
                    if (y < min_y)
                        min_y = y;
                    if (y > max_y)
                        max_y = y;
                    
                    outData[y*imageWidth*3 + x*3 + 1] = (byte)0xFF;
                }
            }
        }
        
        int mw = max_x - min_x;
        int mh = max_y - min_y;
        
        if (mw > 3 && mw < 20 &&
                mh > 3 && mh < 20) {
            if (!stoneTimerRunning) {
                System.out.println("Possible stone detected, starting timer!");
                timer = new Timer();
                timer.schedule(new TimerTask() {
                    public void run() {
                        System.out.println("Stone stable for 1 sec!");
                        stoneOk = true;
                    }
                }, 1000);
                stoneTimerRunning = true;
            }
            if (stoneOk) {
                Stone stone = new Stone(min_x + mw/2.0, min_y + mh/2.0);
                if (gobanCreated) {
                    addNewStone(stone);
                }
                stoneOk = false;
                timer.cancel();
                stoneTimerRunning = false;
            }
        } else {
            if (stoneTimerRunning) {
                System.out.println("Stone dissapeared, cancelling timer..");
                timer.cancel();
                stoneTimerRunning = false;
            }
        }
        
        
        Image img = (new BufferToImage((VideoFormat)outputFormat)).createImage(outBuffer);
        //Graphics gfx = img.getGraphics();
        Graphics2D gfx = (Graphics2D) img.getGraphics();
        gfx.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Composite compo = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f);
        gfx.setComposite(compo);
        gfx.setFont(Font.getFont("Arial"));
        gfx.setColor(Color.white);
        gfx.drawString("test: " + clickedPoints.size(), 0, 0);
        gfx.setColor(Color.red);
        gfx.drawRect(min_x, min_y, max_x-min_x, max_y-min_y);
        
        gfx.setColor(Color.white);
        Point p;
        for (int i=0; i<clickedPoints.size(); i++) {
            p = (Point)clickedPoints.elementAt(i);
            gfx.fillOval(p.x-2,p.y-2, 5,5);
            gfx.drawString("" + (i+1), p.x+2, p.y);
        }
        
        
        if (clickedPoints.size() >= 4 && !gobanCreated) {
            gobanCreated = true;
            
            double diff_x, diff_y;
            p1 = (Point)clickedPoints.elementAt(0);
            p2 = (Point)clickedPoints.elementAt(1);
            p3 = (Point)clickedPoints.elementAt(2);
            p4 = (Point)clickedPoints.elementAt(3);
            
            // Create grid to draw
            diff_x = (double)(p1.x - p2.x) / 18.0;
            diff_y = (double)(p1.y - p2.y) / 18.0;
            for (int n=0; n<19; n++) {
                edge1[n] = new Point((int)(p2.x + diff_x*n), (int)(p2.y + diff_y*n));
            }
            
            diff_x = (double)(p3.x - p4.x) / 18.0;
            diff_y = (double)(p3.y - p4.y) / 18.0;
            for (int n=0; n<19; n++) {
                edge3[n] = new Point((int)(p4.x + diff_x*n), (int)(p4.y + diff_y*n));
            }
            
            diff_x = (double)(p2.x - p3.x) / 18.0;
            diff_y = (double)(p2.y - p3.y) / 18.0;
            for (int n=0; n<19; n++) {
                edge2[n] = new Point((int)(p3.x + diff_x*n), (int)(p3.y + diff_y*n));
            }
            
            diff_x = (double)(p4.x - p1.x) / 18.0;
            diff_y = (double)(p4.y - p1.y) / 18.0;
            for (int n=0; n<19; n++) {
                edge4[n] = new Point((int)(p1.x + diff_x*n), (int)(p1.y + diff_y*n));
            }
            
            // Create goban mapping
            double tmp_x, tmp_y;
            double start_step_x, start_step_y;
            double stop_step_x, stop_step_y;
            double step_x, step_y;
            double start_x, start_y;
            double stop_x, stop_y;
            start_step_x = (double)(p1.x - p2.x) / 18.0;
            start_step_y = (double)(p1.y - p2.y) / 18.0;
            stop_step_x = (double)(p4.x - p3.x) / 18.0;
            stop_step_y = (double)(p4.y - p3.y) / 18.0;
            for (int m=0; m<19; m++) {
                start_x = p2.x + start_step_x*m;
                start_y = p2.y + start_step_y*m;
                stop_x = p3.x + stop_step_x*m;
                stop_y = p3.y + stop_step_y*m;
                
                step_x = (double)(start_x - stop_x) / 18.0;
                step_y = (double)(start_y - stop_y) / 18.0;
                for (int n=0; n<19; n++) {
                    tmp_x = stop_x + step_x*n;
                    tmp_y = stop_y + step_y*n;
                    gobanPoints.addElement(new Stone(tmp_x, tmp_y, m+1, 19-n));
                }
            }
            
            System.out.println("Goban created!");
        }
        
        if (gobanCreated) {
            // Draw grid
            gfx.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
            gfx.setColor(Color.white);
            for (int n=0; n<19; n++) {
                gfx.drawLine(edge1[n].x, edge1[n].y, edge3[18-n].x, edge3[18-n].y);
                gfx.drawLine(edge2[n].x, edge2[n].y, edge4[18-n].x, edge4[18-n].y);
            }
            gfx.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));

            Stone stmp;
            
            // Mark laid stones
            for (int n=0; n<stones.size(); n++) {
                stmp = (Stone)stones.elementAt(n);
                stmp = (Stone)gobanPoints.elementAt(stmp.idx);
                gfx.setColor(Color.green);
                gfx.fillOval((int)stmp.img_x-3, (int)stmp.img_y-3, 7,7);
                gfx.setColor(Color.white);
                gfx.drawString("" + (n+1), (int)stmp.img_x+3, (int)stmp.img_y);
            }
        }
        
        int w = ((VideoFormat)outputFormat).getSize().width;
        int h = ((VideoFormat)outputFormat).getSize().height;
        int[] pixels = new int[w * h];
        PixelGrabber pg = new PixelGrabber(img, 0, 0, w, h, pixels, 0, w);
        try {
            pg.grabPixels();
        } catch (InterruptedException e) {
            System.err.println("interrupted waiting for pixels!");
        }
        
        int iVal;
        for (int i = 0, j = 0; i < pixels.length; i++) {
            iVal = pixels[i];
            outData[j++] = (byte)(iVal >> 0);
            outData[j++] = (byte)(iVal >> 8);
            outData[j++] = (byte)(iVal >> 16);
        }
        
        return BUFFER_PROCESSED_OK;
    }
    
    
    
// Methods for interface PlugIn
    public String getName() {
        return "Stone Detection Codec";
    }
    
    public void open() {
    }
    
    public void close() {
    }
    
    public void reset() {
    }
    
// Methods for interface javax.media.Controls
    public Object getControl(String controlType) {
        System.out.println(controlType);
        return null;
    }
    
    
    public Object[] getControls() {
        return null;
    }
    
    
// Utility methods.
    public Format matches(Format in, Format outs[]) {
        for (int i = 0; i < outs.length; i++) {
            if (in.matches(outs[i]))
                return outs[i];
        }
        
        return null;
    }
    
// Credit : example at www.java.sun.com
    byte[] validateByteArraySize(Buffer buffer,int newSize) {
        Object objectArray=buffer.getData();
        byte[] typedArray;
        
        if (objectArray instanceof byte[]) {     // Has correct type and is not null
            typedArray=(byte[])objectArray;
            if (typedArray.length >= newSize ) { // Has sufficient capacity
                return typedArray;
            }
            
            byte[] tempArray=new byte[newSize];  // Reallocate array
            System.arraycopy(typedArray,0,tempArray,0,typedArray.length);
            typedArray = tempArray;
        } else {
            typedArray = new byte[newSize];
        }
        
        buffer.setData(typedArray);
        return typedArray;
    }
  
    
    public void videoClicked(java.awt.event.MouseEvent evt) {
        if (clickedPoints.size() < 4) {
            clickedPoints.addElement(new Point(evt.getX()/2, evt.getY()/2));
        }
        else {
            System.out.println("Goban is already marked. Use reset to rebuild goban.");
        }
    }
    
    public void saveReference() {
        //System.arraycopy(newImageSquares,0,oldImageSquares,0, newImageSquares.length);
        updateReference = true;
    }
    
    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }
    
    public void resetGoban() {
        clickedPoints.clear();
        stones.clear();
        gobanPoints.clear();
        gobanCreated = false;
    }
    
    public void addNewStone(Stone stone) {
        updateReference = true;
        
        Stone stmp;
        double min_sqdist = 99999999;
        int min_idx = -1;
        double sqdist;
        for (int n=0; n<gobanPoints.size(); n++) {
            stmp = (Stone)gobanPoints.elementAt(n);
            sqdist = Math.pow(stmp.img_x - stone.img_x, 2) + Math.pow(stmp.img_y - stone.img_y, 2); // squared distance
            if (sqdist < min_sqdist) {
                min_idx = n;
                min_sqdist = sqdist;
            }
        }
        if (min_idx != -1) {
            stone.idx = min_idx;
            stmp = (Stone)gobanPoints.elementAt(min_idx);
            stone.board_x = stmp.board_x;
            stone.board_y = stmp.board_y;
            stones.addElement(stone);
            mainptr.newStonePlaced(stone.board_x, stone.board_y);
            System.out.println("New stone added at " + stone.board_x + ", " + stone.board_y + " (" + stone.img_x + ", " + stone.img_y + ")");
        }
    }
    
    public void clearStones() {
        stones.clear();
    }
    
    public void setMainptr(SaiKifuJFrame in) {
        mainptr = in;
    }
}
