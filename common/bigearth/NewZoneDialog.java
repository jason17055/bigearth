package bigearth;

import java.io.*;
import java.awt.*;
import javax.swing.*;

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

	static void showNewZoneDialog(Window ownerWindow, Client client, Location cityLocation)
	{
		NewZoneDialog me = new NewZoneDialog(ownerWindow, client, cityLocation);
		
		ZoneType [] developChoices = me.getDevelopChoices();
		JComboBox<ZoneType> developSelect = new JComboBox<>(developChoices);
		JComponent [] inputs = new JComponent[] {
			new JLabel("Type of land to develop"),
			developSelect
			};

		int rv = JOptionPane.showOptionDialog(ownerWindow, inputs,
			"Develop Land",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE, null, null, null);
		if (rv != JOptionPane.OK_OPTION)
			return;

		ZoneType type = (ZoneType) developSelect.getSelectedItem();
		assert type != null;

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
