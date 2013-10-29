package bigearth;

import java.awt.Point;
import javax.vecmath.*;

/**
 * Defines the transformation of the surface of a spherical-geometry
 * to points on the screen, and vice versa.
 * <p>
 * Note: for spherical geometries, here
 * is how the X-, Y-, and Z-axis directions should be oriented:
 * <ul>
 * <li>Positive Z is towards the North pole.
 * <li>Positive X is towards the intersection of Prime Meridian and Equator.
 * <li>Positive Y is towards 90 deg longitude, 0 deg latitude (eastern hemisphere).
 * </ul>
 * In consequence,
 *  to find latitude, take Math.asin(pt.z),
 *  and to find longitude, take Math.atan2(pt.y, pt.x).
 */
public abstract class AbstractProjection
{
	double zoomFactor = 1.0;
	int xOffset = 0;
	int yOffset = 0;
	double curLatitude;
	double curLongitude;

	Matrix3d transformMatrix = new Matrix3d();
	Matrix3d inverseMatrix = new Matrix3d();

	static final double DEFAULT_WIDTH = 480;
	static final double DEFAULT_HEIGHT = 480;

	//constructor
	protected AbstractProjection()
	{
		updateMatrices();
	}

	public void copySettingsFrom(AbstractProjection otherProj)
	{
		this.zoomFactor = otherProj.zoomFactor;
		this.curLatitude = otherProj.curLatitude;
		this.curLongitude = otherProj.curLongitude;
		this.xOffset = otherProj.xOffset;
		this.yOffset = otherProj.yOffset;
		updateMatrices();
	}

	public boolean isVisible(Point3d pt)
	{
		return (toScreenReal(pt).z >= 0.0);
	}

	//implements MapProjection
	public void scale(double factor)
	{
		zoomFactor *= factor;
		zoomFactor = Math.max(1, zoomFactor);
	}

	void setCoordinates(double latitude, double longitude)
	{
		curLatitude = latitude;
		curLongitude = longitude;
		updateMatrices();
	}

	void setOffset(int xOffset, int yOffset)
	{
		this.xOffset = xOffset;
		this.yOffset = yOffset;
	}

	void updateMatrices()
	{
		// rotate around Z axis for longitude
		Matrix3d rZ = new Matrix3d();
		rZ.rotZ(-curLongitude);

		// rotate around Y axis for latitude
		Matrix3d rY = new Matrix3d();
		rY.rotY(curLatitude);

		transformMatrix.mul(rY, rZ);
		inverseMatrix.invert(transformMatrix);
	}

	void panTo(Point3d pt, Point p)
	{
		//make a copy of pt to avoid corrupting the original
		pt = new Point3d(pt);

		curLatitude = curLongitude = 0;
		transformMatrix.setIdentity();
		inverseMatrix.setIdentity();

		// find desired longitude, latitude
		Point3d qt = fromScreen(p);
		double plgt = Math.atan2(pt.y, pt.x);
		double qlgt = Math.atan2(qt.y, qt.x);
		curLongitude = plgt - qlgt;

		double plat = Math.asin(pt.z);
		double qlat = Math.asin(qt.z);
		curLatitude = plat - qlat;

		updateMatrices();
	}

	public final Point toScreen(Point3d pt)
	{
		Point3d p = toScreenReal(pt);

		double x = zoomFactor * DEFAULT_WIDTH * p.x;
		double y = zoomFactor * DEFAULT_HEIGHT * p.y;

		// prevent extreme screen coordinates from overflowing 32-bit int
		x = Math.max(-64000, Math.min(64000, x));
		y = Math.max(-64000, Math.min(64000, y));

		return new Point(
			(int)(Math.round(x)) + xOffset,
			(int)(Math.round(-y)) + yOffset
			);
	}

	public abstract Point3d toScreenReal(Point3d pt);
	public abstract Point3d fromScreen(Point p);
}

class OrthographicProjection extends AbstractProjection
{
	@Override
	public Point3d toScreenReal(Point3d pt)
	{
		pt = new Point3d(pt);
		transformMatrix.transform(pt);
		return pt;
	}

	@Override
	public Point3d fromScreen(Point p)
	{
		Point3d pt = new Point3d();
		pt.y = (p.x - xOffset) /
			(zoomFactor * DEFAULT_WIDTH);
		pt.z = -(p.y - yOffset) /
			(zoomFactor * DEFAULT_HEIGHT);

		double d = 1 - Math.pow(pt.y,2) - Math.pow(pt.z,2);
		if (d >= 0) {
			pt.x = Math.sqrt(d);
		}
		else {
			pt.x = 0;
		}
		inverseMatrix.transform(pt);
		return pt;
	}
}

class SimpleProjection extends AbstractProjection
{
	@Override
	public void scale(double factor)
	{
		super.scale(factor);
		if (zoomFactor <= 1) {
			curLatitude = 0;
			updateMatrices();
		}
	}

	@Override
	public Point3d toScreenReal(Point3d pt)
	{
		pt = new Point3d(pt);
		transformMatrix.transform(pt);

		double lat = Math.asin(pt.z);
		double lgt = Math.atan2(pt.y, pt.x);
		return new Point3d(
			lgt / Math.PI,
			lat / (Math.PI/2.0),
			0.0);
	}

	@Override
	public Point3d fromScreen(Point p)
	{
		double lat = -(p.y - yOffset) / (zoomFactor * DEFAULT_HEIGHT)
			* (Math.PI/2);
		double lgt = (p.x - xOffset) / (zoomFactor * DEFAULT_WIDTH)
			* (Math.PI);

		double zz = Math.cos(lat);
		Point3d pt = new Point3d(
			Math.cos(lgt) * zz,
			Math.sin(lgt) * zz,
			Math.sin(lat)
			);
		inverseMatrix.transform(pt);
		return pt;
	}
}
