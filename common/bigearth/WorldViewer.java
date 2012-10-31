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
	JToggleButton showRiversBtn;

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

		showRiversBtn = new JToggleButton("Show Rivers");
		showRiversBtn.addActionListener(this);
		buttonsPane.add(showRiversBtn);

		pack();
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		File f = new File("world.txt");
		if (f.exists())
		{
			world = new MakeWorld();
			world.load(f);
			world.generateDrainage();
			world.generateRivers();
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
			regenerate();
		}
		else if (ev.getSource() == showTemperatureBtn)
		{
			showElevationBtn.setSelected(false);
			showRainfallBtn.setSelected(false);
			regenerate();
		}
		else if (ev.getSource() == showRainfallBtn)
		{
			showElevationBtn.setSelected(false);
			showTemperatureBtn.setSelected(false);
			regenerate();
		}
		else if (ev.getSource() == showRiversBtn)
		{
			showElevationBtn.setSelected(false);
			showTemperatureBtn.setSelected(false);
			showRainfallBtn.setSelected(false);
			regenerate();
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

	void regenerate()
	{
		if (world == null)
			return;

		if (showRainfallBtn.isSelected())
		{
			regenerate_Rainfall(world.annualRains);
		}
		else
		{

		int [] colors = new int[world.g.getCellCount()];
		view.rivers = new int[world.g.getCellCount()];

		for (int i = 0; i < colors.length; i++)
		{
			int el = world.elevation[i];

			if (showTemperatureBtn.isSelected())
			{
				int t = world.temperature[i];
				colors[i] = t >= 280 ? 0xaa0000 :
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
				colors[i] = el >= 10 ? 0xffffff :
					el >= 7 ? 0x885500 :
					el >= 3 ? 0x00ff00 :
					el >= 0 ? 0x009900 :
					el >= -3 ? 0x0000ff :
					0x000088;
			}
			else
			{
				colors[i] = el >= 0 ? 0x00ff00 :
					0x0000ff;
			}

			if (showRiversBtn.isSelected())
			{
				if (world.riverVolume[i] > 0)
					view.rivers[i] = world.drainage[i];
			}
			else
			{
				view.rivers = null;
			}
		}
		view.generateImage(world.g, colors);
		}
	}

	void regenerate_Rainfall(int [] rainfall)
	{
		int [] colors = new int[rainfall.length];
		for (int i = 0; i < rainfall.length; i++)
		{
			if (world.elevation[i] >= 0)
			{
				int x = (int)Math.floor(Math.log((double)rainfall[i] / MakeWorld.AVERAGE_RAINFALL) * 4) + RAINFALL_COLORS.length / 2;
				if (x < 0)
					x = 0;
				if (x >= RAINFALL_COLORS.length)
					x = RAINFALL_COLORS.length-1;
	
				colors[i] = RAINFALL_COLORS[x];
			}
			else
			{
				colors[i] = 0xdddddd;
			}
		}
		view.generateImage(world.g, colors);
	}

	class WorldView extends JPanel
		implements MouseListener, MouseMotionListener
	{
		int [] colors;
		int [] rivers;
		BufferedImage image;
		double curLongitude;
		double zoomFactor;
		int yOffset;

		WorldView()
		{
			setPreferredSize(new Dimension(720,360));
			addMouseListener(this);
			addMouseMotionListener(this);
			zoomFactor = 1.0;
			yOffset = 0;
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

		void regenerate()
		{
			if (world == null)
				return;

			SphereGeometry g = world.g;
			Matrix3d rZ = new Matrix3d(
				Math.cos(curLongitude), -Math.sin(curLongitude), 0,
				Math.sin(curLongitude), Math.cos(curLongitude), 0,
				0, 0, 1);


			Point [] pts = new Point[colors.length];
			int [] todo = new int[colors.length];
			for (int i = 0; i < pts.length; i++)
			{
				Point3d p = g.getCenterPoint(i+1);
				rZ.transform(p);

				double lat = Math.asin(p.z);
				double lgt = Math.atan2(p.y, p.x);
				pts[i] = new Point(
					(int)Math.round(360+zoomFactor*lgt*720/(Math.PI*2)),
					(int)Math.round(180-zoomFactor*lat*360/Math.PI) + yOffset
					);

				todo[i] = i;
			}

			this.image = new BufferedImage(720,360,
					BufferedImage.TYPE_INT_RGB);
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

			if (rivers != null)
			{
				Graphics2D gr = image.createGraphics();
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

			repaint();
		}

		public void paint(Graphics g)
		{
			if (image != null)
			{
				g.drawImage(image, 0, 0, Color.WHITE, null);
			}
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
			}
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
					zoomFactor *= 2;
					regenerate();
				}
			}
			else if (ev.getButton() == MouseEvent.BUTTON3)
			{
				zoomFactor/=2;
				if (zoomFactor < 1)
					zoomFactor = 1;
				regenerate();
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