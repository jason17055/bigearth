package bigearth;

public interface Location
{
}

class SimpleLocation implements Location
{
	int regionId;

	public SimpleLocation(int regionId)
	{
		this.regionId = regionId;
	}

	public String toString()
	{
		return new Integer(regionId).toString();
	}
}
