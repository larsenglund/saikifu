/*
 * GobanJPanel.java
 *
 * Created on den 13 december 2005, 01:22
 * Copyright (c) Lars Englund
 */

package saikifupackage;

import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.util.*;


public class GobanJPanel extends JPanel {
    Vector stones = new Vector();
    
    double step_x;
    double step_y;
    int r;
    
    public GobanJPanel() {
        //this.setSize(this.getParent().getWidth(), this.getParent().getHeight());
    }
    
    public void paintComponent(Graphics g) {
        super.paintComponent(g);    // paints background
        
        // Init gfx
        Graphics2D gfx = (Graphics2D) g;
        gfx.setColor(new Color(255,204,51));
        gfx.fillRect(0, 0, this.getWidth(), this.getHeight());
        
        // Draw grid
        gfx.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Composite compo = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f);
        gfx.setComposite(compo);
        gfx.setColor(Color.black);
        step_x = (this.getWidth()-1) / 20.0;
        step_y = (this.getHeight()-1) / 20.0;
        r = (int)step_x/2-1;
        for (int n=1; n<=19; n++) {
            gfx.drawLine((int)step_x, (int)(n*step_y), this.getWidth()-2 - (int)step_x, (int)(n*step_y));
            gfx.drawLine((int)(n*step_x), (int)step_y, (int)(n*step_x), this.getHeight()-2 - (int)step_y);
        }
        
        Point p;
        for (int n=0; n<stones.size(); n++) {
            p = (Point)stones.elementAt(n);
            gfx.setColor(Color.black);
            
            // Draw shadow
            compo = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f);
            gfx.setComposite(compo);
            gfx.fillOval(p.x+1, p.y+1, r*2+2, r*2+2);
            compo = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f);
            gfx.setComposite(compo);
            
            // Draw outline
            gfx.fillOval(p.x-1, p.y-1, r*2+2, r*2+2);
            if (n%2 == 0) {
                gfx.setColor(Color.black);
            } else {
                gfx.setColor(Color.white);
            }
            // Draw stone
            gfx.fillOval(p.x, p.y, r*2, r*2);
        }
    }
    
    public void addStone(Point p) {
        p.x = (int)(p.x * step_x) - r;
        p.y = (int)(p.y * step_y) - r;
        System.out.println("adding " + p.x + ", " + p.y);
        stones.addElement(p);
    }
    
    public void clearStones() {
        stones.clear();
    }
}
