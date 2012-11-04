package bigearth;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.swing.*;
import javax.vecmath.*;

public class WorldView extends JPanel
	implements MouseListener, MouseMotionListener
{
	MakeWorld world;
	int [] colors;
	int [] rivers;
	BufferedImage image;
	double curLongitude;
	double curLatitude;
	double zoomFactor;
	int xOffset;
	int yOffset;
	Matrix3d transformMatrix;
	Matrix3d inverseTransformMatrix;
	ArrayList<Listener> listeners;

	public WorldView()
	{
		listeners = new ArrayList<Listener>();

		setPreferredSize(new Dimension(WIDTH,HEIGHT));
		addMouseListener(this);
		addMouseMotionListener(this);
		zoomFactor = 1.0;
		yOffset = 0;

		transformMatrix = new Matrix3d();
		inverseTransformMatrix = new Matrix3d();
		updateTransformMatrix();
	}

	public interface Listener
	{
		void onTerrainClicked(int regionId, int terrainId);
	}

	public void addListener(Listener l)
	{
		listeners.add(l);
	}

	public void removeListener(Listener l)
	{
		listeners.remove(l);
	}

	boolean tryPixel(int x, int y, int c)
	{
		if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT)
		{
			if ((image.getRGB(x,y) & 0xffffff) == 0)
			{
				image.setRGB(x,y,c);
				return true;
			}
		}
		return false;
	}

	void generateImage(SphereGeometry g, int [] colors)
	{
		this.colors = colors;
		regenerate();
	}

	boolean isVisible(int regionId)
	{
		SphereGeometry g = world.g;
		Point q = toScreen(g.getCenterPoint(regionId));
		return (q.x >= 0 && q.x < WIDTH
			&& q.y >= 0 && q.y < HEIGHT);
	}

	static final int WIDTH = 720;
	static final int HEIGHT = 360;

	Point3d fromScreen(Point p)
	{
		double lat = -(p.y - HEIGHT/2 - yOffset)
			/ (zoomFactor * HEIGHT/Math.PI);
		double lgt = (p.x - WIDTH/2 - xOffset)
			/ (zoomFactor * WIDTH/(2*Math.PI));

		double zz = Math.cos(lat);
		Point3d pt = new Point3d(
			Math.sin(lgt) * zz,
			Math.sin(lat),
			Math.cos(lgt) * zz
			);
		inverseTransformMatrix.transform(pt);
		return pt;
	}

	void toScreen_a(Point3d [] pts, int [] x_coords, int [] y_coords)
	{
		assert pts.length == x_coords.length;
		assert pts.length == y_coords.length;

		for (int i = 0; i < pts.length; i++)
		{
			Point p = toScreen(pts[i]);
			x_coords[i] = p.x;
			y_coords[i] = p.y;
		}
	}

	Point toScreen(Point3d pt)
	{
		pt = new Point3d(pt);
		transformMatrix.transform(pt);

		double lat = Math.asin(pt.y);
		double lgt = Math.atan2(pt.x, pt.z);

		double x = zoomFactor*lgt*WIDTH/(Math.PI*2);
		double y = zoomFactor*lat*HEIGHT/Math.PI;

		// prevent extreme screen coordinates
		// (i.e. coordinates that might overflow 32-bit int)
		x = Math.max(-2*WIDTH, Math.min(2*WIDTH, x));
		y = Math.max(-2*HEIGHT, Math.min(2*HEIGHT, y));

		return new Point(
			(int)Math.round(x) + WIDTH/2 + xOffset,
			(int)Math.round(-y) + HEIGHT/2 + yOffset
			);
	}

	Point toScreen_x(Point3d pt)
	{
		pt = new Point3d(pt);
		transformMatrix.transform(pt);

		if (pt.z <= 0)
			return new Point(-WIDTH,-HEIGHT);

		double x = pt.x * zoomFactor * WIDTH/2;
		double y = pt.y * zoomFactor * HEIGHT/2;

		// prevent extreme screen coordinates
		// (i.e. coordinates that might overflow 32-bit int)
		x = Math.max(-2*WIDTH, Math.min(2*WIDTH, x));
		y = Math.max(-2*HEIGHT, Math.min(2*HEIGHT, y));

		return new Point(
			(int)Math.round(x) + WIDTH/2 + xOffset,
			(int)Math.round(-y) + HEIGHT/2 + yOffset
			);
	}

	void drawMap(BufferedImage image, Point [] pts)
	{
		assert image != null;
		assert pts != null;
		assert pts.length == colors.length;

		int [] todo = new int[pts.length];
		for (int i = 0; i < pts.length; i++)
		{
			todo[i] = i;
		}

		int radius = 0;
		int curCount = todo.length;
		while (todo.length != 0 && radius<100)
		{
			int [] next = new int[curCount];
			int nextCount = 0;
			for (int ii = 0; ii < curCount; ii++)
			{
				int i = todo[ii];
				boolean flag = false;
				int x = pts[i].x;
				int y = pts[i].y;
				if (radius == 0)
				{
					if (tryPixel(x, y, colors[i]))
						flag = true;
				}
				else
				for (int j = 0; j < radius; j++)
				{
					if (tryPixel(x-radius+j, y-j, colors[i]))
						flag = true;
					if (tryPixel(x+j, y-radius+j, colors[i]))
						flag = true;
					if (tryPixel(x+radius-j, y+j, colors[i]))
						flag = true;
					if (tryPixel(x-j, y+radius-j, colors[i]))
						flag = true;
				}
					
				if (flag)
					next[nextCount++] = i;
			}
			todo = next;
			curCount = nextCount;
			radius++;
		}
	}

	void regenerate()
	{
		if (world == null)
			return;

		SphereGeometry g = world.g;
		updateTransformMatrix();

		Point [] pts = new Point[colors.length];
		for (int i = 0; i < pts.length; i++)
		{
			pts[i] = toScreen(g.getCenterPoint(i+1));
		}

		Rectangle [] regionBounds;
		if (zoomFactor >= 4)
		{
			regionBounds = new Rectangle[colors.length];
			for (int i = 0; i < pts.length; i++)
			{
				int min_x = pts[i].x;
				int min_y = pts[i].y;
				int max_x = pts[i].x;
				int max_y = pts[i].y;

				for (int n : g.getNeighbors(i+1))
				{
					Point p = pts[n-1];
					if (p.x > -WIDTH && p.x < min_x) min_x = p.x;
					if (p.y > -HEIGHT && p.y < min_y) min_y = p.y;
					if (p.x < 2*WIDTH && p.x > max_x) max_x = p.x;
					if (p.y < 2*HEIGHT && p.y > max_y) max_y = p.y;
				}
				regionBounds[i] = new Rectangle(
					min_x,
					min_y,
					max_x-min_x+1,
					max_y-min_y+1);
			}
		}
		else
		{
			regionBounds = null;
		}

		Rectangle screen = new Rectangle(0,0,WIDTH,HEIGHT);

		this.image = new BufferedImage(WIDTH,HEIGHT,
				BufferedImage.TYPE_INT_RGB);
		Graphics2D gr = image.createGraphics();

		if (zoomFactor < 8)
		{
			drawMap(image, pts);
		}
		else if (zoomFactor < 16)
		{
			for (int i = 0; i < colors.length; i++)
			{
				if (!screen.intersects(regionBounds[i]))
					continue;

				Point3d [] bb = world.g.getCellBoundary(i+1);
				int [] x_coords = new int[bb.length];
				int [] y_coords = new int[bb.length];
				for (int j = 0; j < bb.length; j++)
				{
					Point p = toScreen(bb[j]);
					x_coords[j] = p.x;
					y_coords[j] = p.y;
				}

				gr.setColor(new Color(colors[i]));
				gr.fillPolygon(x_coords, y_coords, bb.length);
			}
		}

		if (rivers != null)
		{
			gr.setColor(Color.BLACK);
			for (int i = 0; i < rivers.length; i++)
			{
				int d = rivers[i];
				if (d > 0)
				{
					if (Math.abs(pts[i].x - pts[d-1].x) < 500)
						gr.drawLine(pts[i].x,pts[i].y,
							pts[d-1].x,pts[d-1].y);
				}
			}
		}

		if (zoomFactor >= 16)
		{
			gr.setColor(Color.BLACK);
			for (int i = 0; i < world.regions.length; i++)
			{
				if (!screen.intersects(regionBounds[i]))
					continue;

				if (world.regions[i] == null)
					world.enhanceRegion(i+1);

				RegionDetail r = world.regions[i];
				drawRegionDetail(gr, i+1, r);
			}
		}

		repaint();
	}

	void drawGreatCircle(Graphics g, Point3d fromPt, Point3d toPt)
	{
		Point p1 = toScreen(fromPt);
		Point p2 = toScreen(toPt);

		if (Math.abs(p1.x - p2.x) + Math.abs(p1.y - p2.y) > 10)
		{
			Vector3d v = new Vector3d();
			v.sub(fromPt, toPt);
			if (v.length() < 0.0001)
				return;

			v.add(fromPt, toPt);
			v.normalize();
			Point3d centerPt = new Point3d(v);

			drawGreatCircle(g, fromPt, centerPt);
			drawGreatCircle(g, centerPt, toPt);
		}
		else if (Math.abs(p1.x - p2.x) + Math.abs(p1.y - p2.y) < 32)
		{
			g.drawLine(p1.x, p1.y, p2.x, p2.y);
		}
	}

	void drawCoordinateLines(Graphics g)
	{
		g.setColor(new Color(0x888888));
		for (int i = -2; i <= 2; i++)
		{
			double lat = i * Math.PI/6;
			for (int j = -6; j < 6; j++)
			{
				double lgt_1 = j * Math.PI/6;
				double lgt_2 = (j+1) * Math.PI/6;
				drawGreatCircle(g,
					SphereGeometry.fromPolar(lgt_1, lat),
					SphereGeometry.fromPolar(lgt_2, lat)
					);
			}
		}

		for (int i = -5; i <= 6; i++)
		{
			double lgt = i * Math.PI/6;
			for (int j = -3; j < 3; j++)
			{
				double lat_1 = j * Math.PI/6;
				double lat_2 = (j+1) * Math.PI/6;
				drawGreatCircle(g,
					SphereGeometry.fromPolar(lgt, lat_1),
					SphereGeometry.fromPolar(lgt, lat_2)
					);
			}
		}
	}

	void drawRegionDetail(Graphics2D gr, int regionId, RegionDetail r)
	{
		TerrainGeometry tg = new TerrainGeometry(world.g, world.regionDetailLevel);
		int sz = tg.getRegionTileCount(regionId);

		for (int terrainId = 0; terrainId < sz; terrainId++)
		{
			Point3d [] pp = tg.getTerrainBoundary(regionId, terrainId);
			int [] x_coords = new int[pp.length];
			int [] y_coords = new int[pp.length];
			int sum_x = 0;
			int sum_y = 0;
			for (int i = 0; i < pp.length; i++)
			{
				Point p = toScreen(pp[i]);
				x_coords[i] = p.x;
				y_coords[i] = p.y;
				sum_x += p.x;
				sum_y += p.y;
			}

			if (r.terrains[terrainId] != 0)
			{
				gr.setColor(TerrainType.load(r.terrains[terrainId]).color);
			}
			else
			{
				gr.setColor(new Color( colors[regionId-1]) );
			}
			gr.fillPolygon(x_coords, y_coords, pp.length);

			boolean isSelected = regionId == selectedRegion &&
				terrainId == selectedTerrain;

			if (!isSelected)
			{
			gr.setColor(Color.BLACK);
			gr.drawPolygon(x_coords, y_coords, pp.length);
			}

			if (zoomFactor >= 64)
			{
			gr.setColor(Color.BLACK);
			gr.drawString(new Integer(terrainId).toString(),
				sum_x/pp.length,
				sum_y/pp.length);
			}
		}

		if (regionId == selectedRegion)
		{
			Point3d [] pp = tg.getTerrainBoundary(regionId, selectedTerrain);
			int [] x_coords = new int[pp.length];
			int [] y_coords = new int[pp.length];
			toScreen_a(pp, x_coords, y_coords);

			gr.setColor(Color.YELLOW);
			gr.drawPolygon(x_coords, y_coords, pp.length);
		}
	}

	public void paint(Graphics g)
	{
		if (image != null)
		{
			g.drawImage(image, 0, 0, Color.WHITE, null);
		}

		// draw latitude lines
		drawCoordinateLines(g);
	}

	void updateTransformMatrix()
	{
		// rotate around Z axis for longitude
		Matrix3d rZ = new Matrix3d();
		rZ.rotZ(-curLongitude - Math.PI/2);

		// rotate around X axis for latitude
		Matrix3d rX = new Matrix3d();
		rX.rotX(-(Math.PI/2 - curLatitude));

		//transformMatrix.mul(rZ, rX);
		transformMatrix.mul(rX, rZ);
		inverseTransformMatrix.invert(transformMatrix);
	}

	// implements MouseListener
	public void mouseClicked(MouseEvent ev) { }

	// implements MouseListener
	public void mouseEntered(MouseEvent ev) { }

	// implements MouseListener
	public void mouseExited(MouseEvent ev) { }

int selectedRegion;
int selectedTerrain;

	// implements MouseListener
	public void mousePressed(MouseEvent ev)
	{
		if (ev.getButton() == MouseEvent.BUTTON1)
		{
			dragStart = ev.getPoint();
		}
		else if (ev.getButton() == MouseEvent.BUTTON3)
		{
			Point3d pt = fromScreen(ev.getPoint());
			if (zoomFactor >= 16)
			{
				TerrainGeometry tg = new TerrainGeometry(world.g, 2);
				selectedRegion = world.g.findCell(pt);
				selectedTerrain = tg.findTileInRegion(selectedRegion, pt);
				fireTerrainClicked(selectedRegion, selectedTerrain);
				regenerate();
			}
		}
	}

	private void fireTerrainClicked(int regionId, int terrainId)
	{
		for (Listener l : listeners)
		{
			l.onTerrainClicked(regionId, terrainId);
		}
	}

	public void zoomIn()
	{
		zoomFactor *= 2;
		regenerate();
	}

	public void zoomOut()
	{
		zoomFactor/=2;
		if (zoomFactor <= 1)
		{
			zoomFactor = 1;
			curLatitude = 0;
		}
		regenerate();
	}

	// implements MouseListener
	public void mouseReleased(MouseEvent ev)
	{
		if (ev.getButton() == MouseEvent.BUTTON1 && dragStart!=null)
		{
			int d = Math.abs(ev.getX()-dragStart.x)
				+ Math.abs(ev.getY()-dragStart.y);
			if (d>5)
			{
				onDragEnd(ev.getPoint());
			}
			else
			{
				Point3d pt = fromScreen(dragStart);

				TerrainGeometry tg = new TerrainGeometry(world.g, 2);
				selectedRegion = world.g.findCell(pt);
				selectedTerrain = tg.findTileInRegion(selectedRegion, pt);
				regenerate();
			}
			dragStart = null;
		}
		else if (ev.getButton() == MouseEvent.BUTTON3)
		{ //right-click
		}
	}

	// implements MouseMotionListener
	public void mouseDragged(MouseEvent ev)
	{
		if (dragStart != null)
		{
			onDragged(ev.getPoint());
		}
	}

	// implements MouseMotionListener
	public void mouseMoved(MouseEvent ev) {}

	private Point dragStart;
	private void onDragEnd(Point endPoint)
	{
		int xDelta = endPoint.x - dragStart.x;
		int yDelta = endPoint.y - dragStart.y;
		Point3d pt = fromScreen(new Point(WIDTH/2 - xDelta, HEIGHT/2 - yDelta));

		double lat = Math.asin(pt.z);
		double lgt = Math.atan2(pt.y, pt.x);

		if (zoomFactor < 2)
		{
			if (Math.abs(lat) > Math.PI/6)
			{
				lat = (lat>0 ? 1 : -1) * Math.PI/6;
			}
		}
		else if (zoomFactor < 4)
		{
			if (Math.abs(lat) > Math.PI/3)
			{
				lat = (lat>0 ? 1 : -1) * Math.PI/3;
			}
		}

		curLatitude = lat;
		curLongitude = lgt;

		regenerate();
		dragStart = null;
	}

	private void onDragged(Point curPoint)
	{
		//System.out.println(curPoint.x - dragStart.x);
	}
}
