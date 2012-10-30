package bigearth;

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

	WorldViewer()
	{
		super("World Viewer");
		view = new WorldView();
		add(view, BorderLayout.CENTER);

		generateBtn = new JButton("Generate");
		generateBtn.addActionListener(this);
		add(generateBtn, BorderLayout.SOUTH);

		pack();
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
	}

	//implements ActionListener
	public void actionPerformed(ActionEvent ev)
	{
		MakeWorld world = new MakeWorld("w1", 20);
		world.generate();

		int [] colors = new int[world.g.getCellCount()];
		for (int i = 0; i < colors.length; i++)
		{
			colors[i] = world.elevation[i] >= 0 ?
					0x00ff00 : 0x0000ff;
		}
		view.generateImage(world.g, colors);
		view.repaint();
	}

	class WorldView extends JPanel
	{
		BufferedImage image;

		WorldView()
		{
			setPreferredSize(new Dimension(720,360));
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
			Point [] pts = new Point[colors.length];
			int [] todo = new int[colors.length];
			for (int i = 0; i < pts.length; i++)
			{
				Point3d p = g.getCenterPoint(i+1);
				double lat = Math.asin(p.z);
				double lgt = Math.atan2(p.y, p.x);
				pts[i] = new Point(
					(int)Math.round(360+lgt*720/(Math.PI*2)),
					(int)Math.round(180-lat*360/Math.PI)
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
		}

		public void paint(Graphics g)
		{
			if (image != null)
			{
				g.drawImage(image, 0, 0, Color.WHITE, null);
			}
		}
	}
}
