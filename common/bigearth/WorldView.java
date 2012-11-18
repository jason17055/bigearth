package bigearth;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.*;
import java.util.*;
import javax.swing.*;
import javax.vecmath.*;

public class WorldView extends JPanel
	implements MouseListener, MouseMotionListener, MouseWheelListener
{
	MakeWorld world;
	int [] colors;
	BufferedImage image;
	double curLongitude;
	double curLatitude;
	double zoomFactor;
	int xOffset;
	int yOffset;
	Matrix3d transformMatrix;
	Matrix3d inverseTransformMatrix;
	ArrayList<Listener> listeners;
	boolean showRivers;
	boolean allowVertexSelection;

	int selectedRegion;
	Geometry.VertexId selectedVertex;

	static final int UNKNOWN_BIOME_COLOR = 0x888888;

	public WorldView()
	{
		listeners = new ArrayList<Listener>();

		setPreferredSize(new Dimension(WIDTH,HEIGHT));
		addMouseListener(this);
		addMouseMotionListener(this);
		addMouseWheelListener(this);
		zoomFactor = 1.0;
		yOffset = 0;
		showRivers = true;

		transformMatrix = new Matrix3d();
		inverseTransformMatrix = new Matrix3d();
		updateTransformMatrix();
	}

	public interface Listener
	{
		void onRegionSelected(int regionId);
		void onVertexSelected(Geometry.VertexId vertex);
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
				int col = colors[i];
				if (col == 0)
				{
					if (biomeColors.containsKey(world.regions[i].biome))
						col = biomeColors.get(world.regions[i].biome);
					else
						col = UNKNOWN_BIOME_COLOR;
				}

				if (radius == 0)
				{
					if (tryPixel(x, y, col))
						flag = true;
				}
				else
				for (int j = 0; j < radius; j++)
				{
					if (tryPixel(x-radius+j, y-j, col))
						flag = true;
					if (tryPixel(x+j, y-radius+j, col))
						flag = true;
					if (tryPixel(x+radius-j, y+j, col))
						flag = true;
					if (tryPixel(x-j, y+radius-j, col))
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
		else
		{
			//
			// fill region areas
			//
			for (int i = 0; i < regionBounds.length; i++)
			{
				if (!screen.intersects(regionBounds[i]))
					continue;

				Point3d [] bb = world.g.getCellBoundary(i+1);
				int [] x_coords = new int[bb.length];
				int [] y_coords = new int[bb.length];
				toScreen_a(bb, x_coords, y_coords);

				if (colors[i] != 0)
				{
					gr.setColor(new Color(colors[i]));
					gr.fillPolygon(x_coords, y_coords, bb.length);
				}
				else
				{
					RegionDetail r = world.regions[i];
					drawRegionArea(gr, i+1, r, x_coords, y_coords);
				}
			}

		if (showRivers)
		{
			//
			// draw region sides
			//
			for (int i = 0; i < regionBounds.length; i++)
			{
				if (!screen.intersects(regionBounds[i]))
					continue;

				Point3d [] bb = world.g.getCellBoundary(i+1);
				int [] x_coords = new int[bb.length];
				int [] y_coords = new int[bb.length];
				toScreen_a(bb, x_coords, y_coords);

				RegionDetail r = world.regions[i];
				drawRegionBorder(gr, i+1, r, x_coords, y_coords);
			}

			//
			// draw region corners
			//
			for (int i = 0; i < regionBounds.length; i++)
			{
				if (!screen.intersects(regionBounds[i]))
					continue;

				Point3d [] bb = world.g.getCellBoundary(i+1);
				int [] x_coords = new int[bb.length];
				int [] y_coords = new int[bb.length];
				toScreen_a(bb, x_coords, y_coords);

				RegionDetail r = world.regions[i];
				drawRegionCorners(gr, i+1, r, x_coords, y_coords);
			}
		}
				
		} //end if zoom factor >= 8

		if (selectedRegion != 0)
		{
			Point3d [] pp = g.getCellBoundary(selectedRegion);

			int [] x_coords = new int[pp.length];
			int [] y_coords = new int[pp.length];
			toScreen_a(pp, x_coords, y_coords);

			gr.setColor(Color.YELLOW);
			gr.drawPolygon(x_coords, y_coords, pp.length);
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

	private static void drawRiver(Graphics gr, int x0, int y0, int x1, int y1)
	{
		// draw shaft of arrow
		gr.drawLine(x0, y0, x1, y1);
	}

	private static void drawArrow(Graphics gr, int x0, int y0, int x1, int y1)
	{
		// draw shaft of arrow
		gr.drawLine(x0, y0, x1, y1);

		// draw head
		int dx = (x1 - x0) / 5;
		int dy = (y1 - y0) / 5;

		gr.drawLine(x1, y1, x1-dx-dy, y1-dy+dx);
		gr.drawLine(x1, y1, x1-dx+dy, y1-dy-dx);
	}

	static Map<BiomeType, BufferedImage> biomeTextures;
	static Map<BiomeType, Rectangle> biomeMappingRect;
	static Map<BiomeType, Integer> biomeColors;
	static
	{
		biomeTextures = new EnumMap<BiomeType, BufferedImage>(BiomeType.class);
		loadTexture(BiomeType.OCEAN, "ocean");
		loadTexture(BiomeType.DESERT, "desert");
		loadTexture(BiomeType.FOREST, "forest");
		loadTexture(BiomeType.GLACIER, "glacier");
		loadTexture(BiomeType.GRASSLAND, "grassland");
		loadTexture(BiomeType.HILLS, "hills");
		loadTexture(BiomeType.JUNGLE, "jungle");
		loadTexture(BiomeType.MOUNTAIN, "mountains");
		loadTexture(BiomeType.PLAINS, "plains");
		loadTexture(BiomeType.SWAMP, "swamp");
		loadTexture(BiomeType.TUNDRA, "tundra");
		loadTexture(BiomeType.LAKE, "ocean");

		biomeMappingRect = new EnumMap<BiomeType, Rectangle>(BiomeType.class);
		for (BiomeType b : biomeTextures.keySet())
		{
			BufferedImage img = biomeTextures.get(b);
			Rectangle r = new Rectangle(0, 0,
				img.getWidth(),
				img.getHeight()
				);
			biomeMappingRect.put(b, r);
		}

		biomeColors = new EnumMap<BiomeType, Integer>(BiomeType.class);
		biomeColors.put(BiomeType.OCEAN, 0x1e41a4);
		biomeColors.put(BiomeType.LAKE, 0x1e41a4);
		biomeColors.put(BiomeType.DESERT, 0xddbb70);
		biomeColors.put(BiomeType.FOREST, 0x337118);
		biomeColors.put(BiomeType.GLACIER, 0xf6f6f6);
		biomeColors.put(BiomeType.GRASSLAND, 0x0a8403);
		biomeColors.put(BiomeType.HILLS, 0x3f8020);
		biomeColors.put(BiomeType.JUNGLE, 0x367f26);
		biomeColors.put(BiomeType.MOUNTAIN, 0x858767);
		biomeColors.put(BiomeType.PLAINS, 0x7e9e32);
		biomeColors.put(BiomeType.SWAMP, 0x365436);
		biomeColors.put(BiomeType.TUNDRA, 778362);
	}

	static void loadTexture(BiomeType biome, String textureName)
	{
		try
		{
System.err.println("trying to load "+textureName+" texture");
		biomeTextures.put(biome, ImageIO.read(new File("../html/terrain_textures/"+textureName+".png")));
		}
		catch (IOException e)
		{
			//FIXME- do something?
System.err.println(e);
		}
	}

	void drawRegionArea(Graphics gr, int regionId, RegionDetail r, int [] x_coords, int [] y_coords)
	{
		Graphics2D gr2 = (Graphics2D) gr;
		Paint oldPaint = gr2.getPaint();

		BiomeType biome = r.getBiome();
		if (biomeTextures.containsKey(biome))
		{
			Rectangle rect = biomeMappingRect.get(biome);
			TexturePaint p = new TexturePaint(biomeTextures.get(biome), rect);
			gr2.setPaint(p);
		}
		else
		{
			gr2.setColor(new Color(UNKNOWN_BIOME_COLOR));
		}

		gr2.fillPolygon(x_coords, y_coords, x_coords.length);
		gr2.setPaint(oldPaint);
	}

	void drawRegionBorder(Graphics gr, int regionId, RegionDetail r, int [] x_coords, int [] y_coords)
	{
		int n = x_coords.length;

		gr.setColor(Color.BLACK);
		for (int i = 0; i < n; i++)
		{
			RegionSideDetail.SideFeature sf = r.getSideFeature(i);
			if (sf.isRiver())
			{
				Graphics2D gr2 = (Graphics2D) gr;

				Stroke oldStroke = gr2.getStroke();
				if (sf != RegionSideDetail.SideFeature.BROOK)
				{

				gr2.setStroke(new BasicStroke(
					sf == RegionSideDetail.SideFeature.RIVER ? 8.0f : 5.0f
					));
				gr2.setColor(new Color(170,149,53));

				drawRiver(gr,
					x_coords[(i+n-1)%n],
					y_coords[(i+n-1)%n],
					x_coords[i],
					y_coords[i]
					);
				}

				if (sf != RegionSideDetail.SideFeature.BROOK
					|| zoomFactor > 16)
				{

				gr2.setStroke(new BasicStroke(
					sf == RegionSideDetail.SideFeature.RIVER ? 3.0f :
					sf == RegionSideDetail.SideFeature.CREEK ? 2.0f : 1.0f));
				gr2.setColor(Color.BLUE);

				drawRiver(gr,
					x_coords[(i+n-1)%n],
					y_coords[(i+n-1)%n],
					x_coords[i],
					y_coords[i]
					);

				}

				gr2.setStroke(oldStroke);
			}
		}
	}

	void drawRegionCorners(Graphics gr, int regionId, RegionDetail r, int [] x_coords, int [] y_coords)
	{
		int n = x_coords.length;
		for (int i = 0; i < n; i++)
		{
			if (r.corners[i] != null)
			{
				drawRegionCorner(gr, regionId, r, i, x_coords[i], y_coords[i]);
			}
		}
	}

	void drawRegionCorner(Graphics gr, int regionId, RegionDetail r, int cornerIdx, int x, int y)
	{
		Graphics2D gr2 = (Graphics2D) gr;

		RegionCornerDetail.PointFeature type = r.corners[cornerIdx].feature;
		int radius = type == RegionCornerDetail.PointFeature.LAKE ? 10 : 5;

		Paint oldPaint = gr2.getPaint();
		Stroke oldStroke = gr2.getStroke();

		gr2.setStroke(new BasicStroke(5.0f));
		final Color RIVER_BANK_COLOR = new Color(170,149,53);
		gr2.setColor(RIVER_BANK_COLOR);
		gr.drawOval(x-radius,y-radius,2*radius, 2*radius);
		gr2.setStroke(oldStroke);

		if (biomeTextures.containsKey(BiomeType.LAKE))
		{
			gr2.setPaint(new TexturePaint(
				biomeTextures.get(BiomeType.LAKE),
				biomeMappingRect.get(BiomeType.LAKE)
				));
		}
		else
		{
			gr.setColor(new Color(biomeColors.get(BiomeType.LAKE)));
		}

		gr.fillOval(x-radius,y-radius,2*radius,2*radius);

		gr2.setPaint(oldPaint);
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

	// implements MouseWheelListener
	public void mouseWheelMoved(MouseWheelEvent ev)
	{
		if (ev.getWheelRotation() > 0)
			zoomOut();
		else
			zoomIn();
	}

	// implements MouseListener
	public void mouseClicked(MouseEvent ev) { }

	// implements MouseListener
	public void mouseEntered(MouseEvent ev) { }

	// implements MouseListener
	public void mouseExited(MouseEvent ev) { }

	// implements MouseListener
	public void mousePressed(MouseEvent ev)
	{
		if (ev.getButton() == MouseEvent.BUTTON1)
		{
			dragStart = ev.getPoint();

			Point3d pt = fromScreen(ev.getPoint());
			selectNearestTo(pt);
		}
		else if (ev.getButton() == MouseEvent.BUTTON3)
		{
		}
	}

	private void selectNearestTo(Point3d pt)
	{
		int regionId = world.g.findCell(pt);
		if (!allowVertexSelection)
		{
			selectRegion(regionId);
			return;
		}

		Point3d regionCenterPoint = world.g.getCenterPoint(regionId);
		Vector3d v = new Vector3d();
		v.sub(pt, regionCenterPoint);
		double r_dist = v.length();

		Point3d [] borderPoints = world.g.getCellBoundary(regionId);
		int best = -1;
		for (int i = 0; i < borderPoints.length; i++)
		{
			v.sub(pt, borderPoints[i]);
			double n_dist = v.length();
			if (n_dist < r_dist)
			{
				best = i;
				r_dist = n_dist;
			}
		}

		if (best == -1)
		{
			selectRegion(regionId);
			return;
		}

		Geometry.VertexId [] vtxs = world.g.getSurroundingVertices(regionId);
		selectVertex(vtxs[best]);
	}

	private void selectRegion(int regionId)
	{
		selectedRegion = regionId;
		selectedVertex = null;
		fireRegionSelected(selectedRegion);
	}

	private void selectVertex(Geometry.VertexId vtx)
	{
		selectedRegion = 0;
		selectedVertex = vtx;
		fireVertexSelected(selectedVertex);
	}

	private void fireRegionSelected(int regionId)
	{
		for (Listener l : listeners)
			l.onRegionSelected(regionId);
	}

	private void fireVertexSelected(Geometry.VertexId vertex)
	{
		for (Listener l : listeners)
			l.onVertexSelected(vertex);
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
