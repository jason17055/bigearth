var COMMODITY_TYPES = {
	'pig': {
		isLivestock: true,
		nutrition: 100
		},
	'sheep': {
		isLivestock: true,
		nutrition: 100
		},
	'wood': {
		weight: 500
		},
	'wheat': {
		nutrition: 100,
		weight: 100
		},
	'meat': {
		nutrition: 100,
		weight: 100
		}
	};

function getCommodityTypeInfo(commodityType)
{
	var info = COMMODITY_TYPES[commodityType];
	return info;
}

exports.getCommodityTypeInfo = getCommodityTypeInfo;
