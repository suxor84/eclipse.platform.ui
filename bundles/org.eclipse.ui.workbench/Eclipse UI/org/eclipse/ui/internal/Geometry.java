/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.internal;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;

/**
 * Contains static methods for performing simple geometric operations
 * on the SWT geometry classes.
 */
public class Geometry {

	private Geometry() {
	}

	/**
	 * Returns the square of the distance between two points
	 * 
	 * @param p1
	 * @param p2
	 * @return
	 */
	public static int distanceSquared(Point p1, Point p2) {
		int term1 = p1.x - p2.x;
		int term2 = p1.y - p2.y;
		return term1 * term1 + term2 * term2;
	}

	/**
	 * Returns the magnitude of the given 2d vector (represented as a Point)
	 *  
	 * @param p point representing the 2d vector whose magnitude is being computed
	 * @return
	 */
	public static double magnitude(Point p) {
		return Math.sqrt(magnitudeSquared(p));
	}

	/**
	 * Returns the square of the magnitude of the given 2-space vector (represented
	 * using a point)
	 * 
	 * @param p
	 * @return the square of the magnitude of the given vector
	 */
	public static int magnitudeSquared(Point p) {
		return p.x * p.x + p.y * p.y;
	}

	/**
	 * Returns the area of the rectangle
	 * 
	 * @param rectangle
	 * @return
	 */
	public static Point getSize(Rectangle rectangle) {
		return new Point(rectangle.width, rectangle.height);
	}

	/**
	 * Returns a new point whose coordinates are the minimum of the coordinates of the
	 * given points
	 * 
	 * @param p1
	 * @param p2
	 * @return a new point whose coordinates are the minimum of the coordinates of the
	 * given points
	 */
	public static Point min(Point p1, Point p2) {
		return new Point(Math.min(p1.x, p2.x), Math.min(p1.y, p2.y));
	}

	/**
	 * Returns a direction vector in the given direction with the given
	 * magnitude.
	 * 
	 * @param distance magnitude of the vector
	 * @param direction one of SWT.TOP, SWT.BOTTOM, SWT.LEFT, or SWT.RIGHT
	 * @return a point representing a vector in the given direction with the given magnitude
	 */
	public static Point getDirectionVector(int distance, int direction) {
		switch (direction) {
			case SWT.TOP: return new Point(0, -distance);
			case SWT.BOTTOM: return new Point(0, distance);
			case SWT.LEFT: return new Point(-distance, 0);
			case SWT.RIGHT: return new Point(distance, 0);
		}
		
		return new Point(0,0);
	}

	/**
	 * Returns the point in the center of the given rectangle.
	 * 
	 * @param rect
	 * @return
	 */
	public static Point centerPoint(Rectangle rect) {
		return new Point(rect.x + rect.width / 2, rect.y + rect.height / 2);
	}

	/**
	 * Returns the height or width of the given rectangle.
	 * 
	 * @param toMeasure rectangle to measure
	 * @param width returns the width if true, and the height if false
	 * @return
	 */
	public static int getDimension(Rectangle toMeasure, boolean width) {
		if (width) {
			return toMeasure.width;
		} else {
			return toMeasure.height;
		}
	}

	/**
	 * Returns the distance of the given point from a particular side of the given rectangle.
	 * Returns negative values for points outside the rectangle.
	 * 
	 * @param rectangle
	 * @param testPoint
	 * @param edgeOfInterest
	 * @return the distance of the given point from the edge of the rectangle.
	 */
	public static int getDistanceFromEdge(Rectangle rectangle, Point testPoint, int edgeOfInterest) {
		switch(edgeOfInterest) {
		}
		
		return 0;
	}

	/**
	 * Extrudes the given edge inward by the given distance. That is, if one side of the rectangle
	 * was sliced off with a given thickness, this returns the rectangle that forms the slice. Note
	 * that the returned rectangle will be inside the given rectangle.
	 * 
	 * @param toExtrude the rectangle to extrude. The resulting rectangle will share three sides
	 * with this rectangle.
	 * @param size distance to extrude. A negative size will extrude outwards (that is, the resulting
	 * rectangle will overlap the original iff this is positive). 
	 * @param orientation the side to extrude.  One of SWT.LEFT, SWT.RIGHT, SWT.TOP, or SWT.BOTTOM. The 
	 * resulting rectangle will always share this side with the original rectangle.
	 * @return a rectangle formed by extruding the given side of the rectangle by the given distance.
	 */
	public static Rectangle getExtrudedEdge(Rectangle toExtrude, int size, int orientation) {
		Rectangle bounds = new Rectangle(toExtrude.x, toExtrude.y, toExtrude.width, toExtrude.height);
		
		if (!isHorizontal(orientation)) {
			bounds.width = size;
		} else {
			bounds.height = size;
		}
		
		switch(orientation) {
		case SWT.RIGHT:
			bounds.x = toExtrude.x + toExtrude.width - bounds.width;
			break;
		case SWT.BOTTOM:
			bounds.y = toExtrude.y + toExtrude.height - bounds.height;
			break;
		}
	
		normalize(bounds);
		
		return bounds;
	}

	/**
	 * Returns the opposite of the given direction. That is, returns SWT.LEFT if
	 * given SWT.RIGHT and visa-versa.
	 * 
	 * @param swtDirectionConstant one of SWT.LEFT, SWT.RIGHT, SWT.TOP, or SWT.BOTTOM
	 * @return one of SWT.LEFT, SWT.RIGHT, SWT.TOP, or SWT.BOTTOM
	 */
	public static int getOppositeSide(int swtDirectionConstant) {
		switch(swtDirectionConstant) {
		}
		
		return swtDirectionConstant;
	}

	/**
	 * Converts the given boolean into an SWT orientation constant.
	 * 
	 * @param horizontal if true, returns SWT.HORIZONTAL. If false, returns SWT.VERTICAL 
	 * @return SWT.HORIZONTAL or SWT.VERTICAL.
	 */
	public static int getSwtHorizontalOrVerticalConstant(boolean horizontal) {
		if (horizontal) {
			return SWT.HORIZONTAL;
		} else {
			return SWT.VERTICAL;
		}
	}

	/**
	 * Returns true iff the given SWT side constant corresponds to a horizontal side
	 * of a rectangle. That is, returns true for the top and bottom but false for the
	 * left and right.
	 * 
	 * @param swtSideConstant one of SWT.TOP, SWT.BOTTOM, SWT.LEFT, or SWT.RIGHT
	 * @return true iff the given side is horizontal.
	 */
	public static boolean isHorizontal(int swtSideConstant) {
		return !(swtSideConstant == SWT.LEFT || swtSideConstant == SWT.RIGHT);
	}

	/**
	 * Moves the given rectangle by the given delta.
	 * 
	 * @param rect
	 * @param delta
	 */
	public static void moveRectangle(Rectangle rect, Point delta) {
		rect.x += delta.x;
		rect.y += delta.y;
	}

	/**
	 * Normalizes the given rectangle. That is, any rectangle with
	 * negative width or height becomes a rectangle with positive
	 * width or height that extends to the upper-left of the original
	 * rectangle. 
	 * 
	 * @param rect
	 */
	public static void normalize(Rectangle rect) {
		if (rect.width < 0) {
			rect.width = -rect.width;
			rect.x -= rect.width;
		}
		
		if (rect.height < 0) {
			rect.height = -rect.height;
			rect.y -= rect.height;
		}
	}

	/**
	 * Converts the given rectangle from the local coordinate system of the given object
	 * into display coordinates
	 * 
	 * @param coordinateSystem
	 * @param toConvert
	 * @return
	 */
	public static Rectangle toDisplay(Control coordinateSystem, Rectangle toConvert) {
		Point start = coordinateSystem.toDisplay(toConvert.x, toConvert.y);
		return new Rectangle(start.x, start.y, toConvert.width, toConvert.height);
	}
	
}
