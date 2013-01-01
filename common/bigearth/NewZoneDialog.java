package bigearth;

import java.io.*;
import java.net.URL;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;

public class NewZoneDialog
{
	Client client;
	Location cityLocation;

	NewZoneDialog(Window ownerWindow, Client client, Location cityLocation)
	{
		this.client = client;
		this.cityLocation = cityLocation;
	}

	private ZoneType [] getDevelopChoices()
	{
		CityInfo city;
		try
		{
			city = client.getCity(cityLocation);
			if (city != null && city.hasNewZoneChoices())
				return city.newZoneChoices.toArray(new ZoneType[0]);
		}
		catch (IOException e)
		{
			//FIXME
			e.printStackTrace(System.err);
		}
		return new ZoneType[0];
	}

	static class MyZoneCellRenderer implements ListCellRenderer<ZoneType>
	{
		JPanel mainPane;
		JLabel iconLabel;
		JLabel typeLabel;
		JLabel detailLabel;

		public MyZoneCellRenderer()
		{
			mainPane = new JPanel();
			mainPane.setLayout(new BorderLayout());
			mainPane.setOpaque(true);

			JPanel gridPane = new JPanel();
			gridPane.setLayout(new GridBagLayout());
			gridPane.setBorder(
				BorderFactory.createEmptyBorder(4,4,4,4)
				);
			gridPane.setOpaque(false);

			GridBagConstraints c = new GridBagConstraints();

			iconLabel = new JLabel();
			c.gridx = c.gridy = 0;
			c.gridwidth = 1;
			c.gridheight = 2;
			c.fill = GridBagConstraints.BOTH;
			c.insets = new Insets(0, 0, 0, 6);
			gridPane.add(iconLabel, c);

			typeLabel = new JLabel();
			c.gridx = 1;
			c.gridheight = 1;
			c.anchor = GridBagConstraints.NORTHWEST;
			c.weightx = 1.0;
			c.weighty = 0.0;
			c.fill = GridBagConstraints.NONE;
			c.insets = new Insets(0, 0, 0, 0);
			gridPane.add(typeLabel, c);

			detailLabel = new JLabel();
			Font f = detailLabel.getFont();
			detailLabel.setFont(f.deriveFont(
					f.getStyle() & ~Font.BOLD,
					(float)(f.getSize2D() * 0.85)));
			c.gridx = 1;
			c.gridy = 1;
			c.fill = GridBagConstraints.NONE;
			c.anchor = GridBagConstraints.NORTHWEST;
			c.weighty = 1.0;
			gridPane.add(detailLabel, c);

			mainPane.add(gridPane);
		}

		//implements ListCellRenderer
		public Component getListCellRendererComponent(JList<? extends ZoneType> list, ZoneType zoneType, int index, boolean isSelected, boolean cellHasFocus)
		{
			URL zoneIconUrl = zoneType.getIconResource();
			ImageIcon zoneIcon = zoneIconUrl != null ? new ImageIcon(zoneIconUrl) : null;
			iconLabel.setIcon(zoneIcon);
			typeLabel.setText(zoneType.getDisplayName());
			detailLabel.setText(null);

			if (isSelected)
			{
				mainPane.setBackground(list.getSelectionBackground());
				mainPane.setForeground(list.getSelectionForeground());
			}
			else
			{
				mainPane.setBackground(list.getBackground());
				mainPane.setForeground(list.getForeground());
			}

			Border b = null;
			if (cellHasFocus)
			{
				if (isSelected)
					b = UIManager.getBorder("List.focusSelectedCellHighlightBorder");
				if (b == null)
					b = UIManager.getBorder("List.focusCellHighlightBorder");
			}
			else
			{
				b = NO_FOCUS_BORDER;
			}
			mainPane.setBorder(b);

			return mainPane;
		}

		static final Border NO_FOCUS_BORDER = new EmptyBorder(1,1,1,1);
	}

	static void showNewZoneDialog(Window ownerWindow, Client client, Location cityLocation)
	{
		NewZoneDialog me = new NewZoneDialog(ownerWindow, client, cityLocation);
		
		ZoneType [] developChoices = me.getDevelopChoices();
		JList<ZoneType> developList = new JList<>(developChoices);
		developList.setCellRenderer(new MyZoneCellRenderer());
		developList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		developList.setVisibleRowCount(-1);
		developList.setLayoutOrientation(JList.HORIZONTAL_WRAP);

		JScrollPane scroll = new JScrollPane(developList);
		scroll.setPreferredSize(new Dimension(550,212));

		JComponent [] inputs = new JComponent[] {
			new JLabel("Select type of land to develop"),
			scroll
			};

		int rv = JOptionPane.showOptionDialog(ownerWindow, inputs,
			"Develop Land",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE, null, null, null);
		if (rv != JOptionPane.OK_OPTION)
			return;

		ZoneType type = developList.getSelectedValue();
		if (type == null)
			return;

		try {

		DevelopCommand c = new DevelopCommand();
		c.fromZoneType = ZoneType.NATURAL;
		c.toZoneType = type;
		client.sendCityOrders(cityLocation, c);

		}
		catch (Exception e)
		{
			e.printStackTrace(System.err);
			JOptionPane.showMessageDialog(ownerWindow, e,
				"Error",
				JOptionPane.ERROR_MESSAGE);
		}
	}
}
