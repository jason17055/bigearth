package bigearth;

import java.util.*;
import javax.swing.table.*;

public class CommodityBagTableModel extends AbstractTableModel
{
	static final String [] COLUMN_NAMES = { "Commodity", "Amount" };
	ArrayList<CommodityType> typesList = new ArrayList<>();
	ArrayList<Long> quantitiesList = new ArrayList<>();

	@Override
	public int getRowCount()
	{
		return typesList.size();
	}

	@Override
	public int getColumnCount()
	{
		return COLUMN_NAMES.length;
	}

	@Override
	public Class getColumnClass(int columnIndex)
	{
		switch (columnIndex) {
		case 0: return CommodityType.class;
		case 1: return Long.class;
		default: throw new IllegalArgumentException();
		}
	}

	@Override
	public String getColumnName(int column)
	{
		return COLUMN_NAMES[column];
	}

	@Override
	public Object getValueAt(int row, int column)
	{
		if (column == 0)
			return typesList.get(row);
		else if (column == 1)
			return quantitiesList.get(row);
		else
			throw new IllegalArgumentException();
	}

	public void refreshFrom(CommoditiesBag srcBag)
	{
		assert typesList.size() == quantitiesList.size();

		HashSet<CommodityType> seen = new HashSet<>();
		for (int i = 0; i < typesList.size(); i++)
		{
			CommodityType ct = typesList.get(i);
			seen.add(ct);

			long newQty = srcBag.getQuantity(ct);
			if (newQty == 0)
			{
				typesList.remove(i);
				quantitiesList.remove(i);
				fireTableRowsDeleted(i, i);
				i--;
			}
			else if (newQty != quantitiesList.get(i).longValue())
			{
				quantitiesList.set(i, newQty);
				fireTableRowsUpdated(i, i);
			}
		}

		for (CommodityType ct : srcBag.getCommodityTypesArray())
		{
			if (!seen.contains(ct))
			{
				typesList.add(ct);
				quantitiesList.add(srcBag.getQuantity(ct));
				fireTableRowsInserted(
					typesList.size()-1, typesList.size()-1
					);
			}
		}
	}
}
