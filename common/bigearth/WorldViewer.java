package bigearth;

import java.io.*;
import java.util.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.vecmath.*;

public class WorldViewer extends JFrame
	implements ActionListener
{
	public static void main(String [] args)
		throws Exception
	{
		new WorldViewer().setVisible(true);
	}

	WorldView view;
	JButton generateBtn;
	JToggleButton showElevationBtn;
	JToggleButton showTemperatureBtn;
	JToggleButton showRainfallBtn;
	JToggleButton showFloodsBtn;
	JToggleButton showRiversBtn;
	JButton zoomInBtn;
	JButton zoomOutBtn;

	JPanel toolsPane;
	Map<String, JToggleButton> toolBtns;

	private void addToolButton(String command)
	{
		JToggleButton btn = new JToggleButton(command);
		btn.setActionCommand(command);
		btn.addActionListener(this);
		toolsPane.add(btn);
		toolBtns.put(command, btn);
	}

	WorldViewer() throws IOException
	{
		super("World Viewer");
		view = new WorldView();
		add(view, BorderLayout.CENTER);

		toolsPane = new JPanel();
		toolsPane.setLayout(new BoxLayout(toolsPane, BoxLayout.Y_AXIS));
		add(toolsPane, BorderLayout.WEST);

		toolBtns = new HashMap<String, JToggleButton>();
		addToolButton("hand");
		addToolButton("grass");
		addToolButton("ocean");
		addToolButton("lake");

		selectedTool = "hand";
		toolBtns.get("hand").setSelected(true);

		JPanel buttonsPane = new JPanel();
		add(buttonsPane, BorderLayout.SOUTH);

		generateBtn = new JButton("Generate");
		generateBtn.addActionListener(this);
		buttonsPane.add(generateBtn);

		showElevationBtn = new JToggleButton("Show Elevations");
		showElevationBtn.addActionListener(this);
		buttonsPane.add(showElevationBtn);

		showTemperatureBtn = new JToggleButton("Show Temperatures");
		showTemperatureBtn.addActionListener(this);
		buttonsPane.add(showTemperatureBtn);

		showRainfallBtn = new JToggleButton("Show Rains");
		showRainfallBtn.addActionListener(this);
		buttonsPane.add(showRainfallBtn);

		showFloodsBtn = new JToggleButton("Show Floods");
		showFloodsBtn.addActionListener(this);
		buttonsPane.add(showFloodsBtn);

		showRiversBtn = new JToggleButton("Show Rivers");
		showRiversBtn.addActionListener(this);
		buttonsPane.add(showRiversBtn);

		zoomInBtn = new JButton("Zoom In");
		zoomInBtn.addActionListener(this);
		buttonsPane.add(zoomInBtn);

		zoomOutBtn = new JButton("Zoom Out");
		zoomOutBtn.addActionListener(this);
		buttonsPane.add(zoomOutBtn);

		pack();
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		File f = new File("world.txt");
		if (f.exists())
		{
			world = new MakeWorld();
			world.load(f);
			regenerate();
		}
	}

	MakeWorld world;
	String selectedTool;

	void onToolSelected(String toolName)
	{
		if (toolBtns.get(toolName).isSelected())
		{
			// unselect all other tools
			for (String s : toolBtns.keySet())
			{
				if (!s.equals(toolName))
					toolBtns.get(s).setSelected(false);
			}
			selectedTool = toolName;
		}
		else
		{
			// select the hand tool
			selectedTool = "hand";
			toolBtns.get(selectedTool).setSelected(true);
		}
	}

	//implements ActionListener
	public void actionPerformed(ActionEvent ev)
	{
		if (toolBtns.containsKey(ev.getActionCommand()))
		{
			onToolSelected(ev.getActionCommand());
		}
		else if (ev.getSource() == generateBtn)
		{
			this.world = new MakeWorld("w1", 20);
			world.generate();
			try
			{
			world.save(new File("world.txt"));
			} catch (IOException e)
			{
				System.err.println(e.getMessage());
			}
			regenerate();
	
		}
		else if (ev.getSource() == showElevationBtn)
		{
			showTemperatureBtn.setSelected(false);
			showRainfallBtn.setSelected(false);
			showFloodsBtn.setSelected(false);
			regenerate();
		}
		else if (ev.getSource() == showTemperatureBtn)
		{
			showElevationBtn.setSelected(false);
			showRainfallBtn.setSelected(false);
			showFloodsBtn.setSelected(false);
			regenerate();
		}
		else if (ev.getSource() == showRainfallBtn)
		{
			showElevationBtn.setSelected(false);
			showTemperatureBtn.setSelected(false);
			regenerate();
		}
		else if (ev.getSource() == showFloodsBtn)
		{
			showElevationBtn.setSelected(false);
			showTemperatureBtn.setSelected(false);
			regenerate();
		}
		else if (ev.getSource() == showRiversBtn)
		{
			regenerate();
		}
		else if (ev.getSource() == zoomInBtn)
		{
			view.zoomIn();
		}
		else if (ev.getSource() == zoomOutBtn)
		{
			view.zoomOut();
		}
	}

	int [] RAINFALL_COLORS = {
		0xc2533c, 0xc95c34, 0xd46e2c, 0xde7f23,
		0xe7951d, 0xeca815, 0xf0bd11, 0xf5d40e,
		0xf6ea17, 0xf1f418, 0xb8de14, 0x7ac221,
		0x3ba42a, 0x199332, 0x0d8e35, 0x1c9744,
		0x1e9c65, 0x199980, 0x219592, 0x1a7f90,
		0x186388, 0x1a4780, 0x112d7a
		};

	int [] ELEVATION_COLORS = {
		// below sea-level elevations
		0x000044, 0x000088, 0x0000cc, 0x0000ff,

		// at sea-level and above
		0x008800, 0x00aa00, 0x00cc00, 0x00e000,
		0x00ff00, 0x44ff00, 0x88ff00, 0xcccc00,
		0xddaa00, 0xff9900, 0xff9944, 0xff4444,
		0xffcccc, 0xffffff
		};

	void regenerate()
	{
		if (world == null)
			return;

		int [] colors = new int[world.g.getCellCount()];
		for (int i = 0; i < colors.length; i++)
		{
			colors[i] = colorOfRegion(i+1);
		}

		view.rivers = new int[world.g.getCellCount()];
		for (int i = 0; i < colors.length; i++)
		{
			if (showRiversBtn.isSelected())
			{
				if (world.riverVolume[i] > 0)
					view.rivers[i] = world.drainage[i];
			}
			else
			{
				view.rivers[i] = 0;
			}
		}
		view.generateImage(world.g, colors);
	}

	int colorOfRegion(int regionId)
	{
		int i = regionId - 1;

		int el = world.elevation[i];

		if (showTemperatureBtn.isSelected())
		{
			int t = world.temperature[i];
			return t >= 280 ? 0xaa0000 :
				t >= 235 ? 0xdd2200 :
				t >= 180 ? 0xff5500 :
				t >= 122 ? 0xffee00 :
				t >= 80 ? 0x44cc00 :
				t >= 30 ? 0x00ccee :
				t >= -20 ? 0x2233aa :
				0xbb55cc;
		}
		else if (showElevationBtn.isSelected())
		{
			int x = el + 4;
			x = (x >= 0 ? x : 0);
			x = (x < ELEVATION_COLORS.length ? x :
				ELEVATION_COLORS.length - 1);
			return ELEVATION_COLORS[x];
		}
		else if (showRainfallBtn.isSelected() || showFloodsBtn.isSelected())
		{
			boolean showRains = showRainfallBtn.isSelected();
			boolean showFloods = showFloodsBtn.isSelected();

			if (el >= 0)
			{
				int moisture = 0
					+ (showRains ? world.annualRains[i] : 0)
					+ (showFloods ? world.floods[i] * 360 : 0)
					;
				int x = (int)Math.floor(Math.log((double)moisture / MakeWorld.AVERAGE_RAINFALL) * 4) + RAINFALL_COLORS.length / 2;
				if (x < 0)
					x = 0;
				if (x >= RAINFALL_COLORS.length)
					x = RAINFALL_COLORS.length-1;
	
				return RAINFALL_COLORS[x];
			}
			else
			{
				return 0xdddddd;
			}
		}
		else
		{
			return el < 0 ? 0x0000ff :
				world.lakeLevel[i] > el ? 0x6666ff :
				0x00ff00;
		}
	}

	void onTerrainClicked(int regionId, int terrainId)
	{
		RegionDetail r = world.regions[regionId-1];
		if (selectedTool.equals("grass"))
		{
			r.setTerrainType(terrainId, TerrainType.GRASS);
		}
		else if (selectedTool.equals("ocean"))
		{
			r.setTerrainType(terrainId, TerrainType.OCEAN);
		}
		else if (selectedTool.equals("lake"))
		{
			r.setTerrainType(terrainId, TerrainType.LAKE);
		}
	}

	class WorldView extends JPanel
		implements MouseListener, MouseMotionListener
	{
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

		WorldView()
		{
			setPreferredSize(new Dimension(WIDTH,HEIGHT));
			addMouseListener(this);
			addMouseMotionListener(this);
			zoomFactor = 1.0;
			yOffset = 0;

			transformMatrix = new Matrix3d();
			inverseTransformMatrix = new Matrix3d();
			updateTransformMatrix();
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

		void drawRegionDetail(Graphics2D gr, int regionId, RegionDetail r)
		{
			TerrainGeometry tg = new TerrainGeometry(world.g, 2);
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
					onTerrainClicked(selectedRegion, selectedTerrain);
					regenerate();
				}
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
}
