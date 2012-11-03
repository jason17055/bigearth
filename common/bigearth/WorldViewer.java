package bigearth;

import java.io.*;
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

	WorldViewer() throws IOException
	{
		super("World Viewer");
		view = new WorldView();
		add(view, BorderLayout.CENTER);

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

	//implements ActionListener
	public void actionPerformed(ActionEvent ev)
	{
		if (ev.getSource() == generateBtn)
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
				world.lakeLevel[i] > el ? 0x8888ff :
				0x00ff00;
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
			setPreferredSize(new Dimension(720,360));
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
			if (x >= 0 && x < 720 && y >= 0 && y < 360)
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
			return (q.x >= 0 && q.x < 720
				&& q.y >= 0 && q.y < 360);
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
				Math.cos(lgt) * zz,
				Math.sin(lat),
				-Math.sin(lgt) * zz
				);
			inverseTransformMatrix.transform(pt);
			return pt;
		}

		Point toScreen(Point3d pt)
		{
			pt = new Point3d(pt);
			transformMatrix.transform(pt);

			double lat = Math.asin(pt.y);
			double lgt = Math.atan2(-pt.z, pt.x);
			return new Point(
				(int)Math.round(WIDTH/2+zoomFactor*lgt*WIDTH/(Math.PI*2)) + xOffset,
				(int)Math.round(HEIGHT/2-zoomFactor*lat*HEIGHT/Math.PI) + yOffset
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

			this.image = new BufferedImage(720,360,
					BufferedImage.TYPE_INT_RGB);
			if (zoomFactor < 16)
			{
				drawMap(image, pts);
			}

			Graphics2D gr = image.createGraphics();
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
					if (!(pts[i].x >= -50 && pts[i].x < 720+50
					&& pts[i].y >= -50 && pts[i].y < 360+50))
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

			int [] neighborRegions = new int[3];
			int [] neighborTiles = new int[3];
			if (selectedRegion != 0)
			tg.getNeighborTiles(neighborRegions, neighborTiles, selectedRegion, selectedTerrain);

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
				gr.setColor(new Color(
					regionId == selectedRegion &&
					terrainId == selectedTerrain ? 0xffffff :
					(regionId == neighborRegions[0] && terrainId == neighborTiles[0]) ||
					(regionId == neighborRegions[1] && terrainId == neighborTiles[1]) ||
					(regionId == neighborRegions[2] && terrainId == neighborTiles[2]) ? 0xffff00 :
					colors[regionId-1]));
				gr.fillPolygon(x_coords, y_coords, pp.length);

				gr.setColor(Color.BLACK);
				gr.drawPolygon(x_coords, y_coords, pp.length);

				gr.drawString(new Integer(terrainId).toString(),
					sum_x/pp.length,
					sum_y/pp.length);
			}

			if (regionId == selectedRegion)
			{
				Point3d [] pp = tg.getTerrainBoundary(regionId, selectedTerrain);
System.out.println("is this clockwise or counter-clockwise?");
System.out.println(pp[0]);
System.out.println(pp[1]);
System.out.println(pp[2]);
				Point p = toScreen(pp[0]);
				gr.setColor(Color.RED);
				gr.fillOval(p.x-5,p.y-5,10,10);

				p = toScreen(pp[1]);
				gr.setColor(Color.GREEN);
				gr.fillOval(p.x-5,p.y-5,10,10);

				p = toScreen(pp[2]);
				gr.setColor(Color.BLUE);
				gr.fillOval(p.x-5,p.y-5,10,10);
			}
		}

		public void paint(Graphics g)
		{
			if (image != null)
			{
				g.drawImage(image, 0, 0, Color.WHITE, null);
			}
		}

		void updateTransformMatrix()
		{
			// rotate around Z axis for longitude
			Matrix3d rZ = new Matrix3d();
			rZ.rotZ(curLongitude);

			// rotate around X axis for latitude
			Matrix3d rX = new Matrix3d();
			rX.rotX(-(Math.PI/2 - curLatitude));

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
		}

		public void zoomIn()
		{
			zoomFactor *= 2;
			regenerate();
		}

		public void zoomOut()
		{
			zoomFactor/=2;
			if (zoomFactor < 1)
				zoomFactor = 1;
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
				System.out.println("  region "+selectedRegion+", terrain " + selectedTerrain);
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
			double dist = (endPoint.x - dragStart.x) / zoomFactor;
			curLongitude += dist * Math.PI * 2 / 720.0;
			yOffset += (endPoint.y - dragStart.y);
			regenerate();
			dragStart = null;
		}

		private void onDragged(Point curPoint)
		{
			//System.out.println(curPoint.x - dragStart.x);
		}
	}
}
