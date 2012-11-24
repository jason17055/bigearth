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

	public boolean equals(Object obj)
	{
		if (obj instanceof SimpleLocation)
		{
			return this.regionId == ((SimpleLocation) obj).regionId;
		}
		return false;
	}

	public int hashCode()
	{
		return regionId;
	}
}
