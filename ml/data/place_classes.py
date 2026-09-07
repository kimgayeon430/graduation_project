"""Places365 장면 클래스 → Travel Mission 카테고리 매핑.

Places365 라벨은 데이터셋마다 표기가 다르다.
  - 원본:            "/a/airport_terminal", "bazaar-indoor"
  - 일부 HF 미러:    "airport terminal", "bazaar indoor"
`_norm()` 으로 소문자·구분자 통일 후 비교하므로 어느 표기든 매칭된다.
필요에 따라 아래 목록을 넓혀 가며 조정하라.
"""

# 투어: 관광지·랜드마크·전망·야외 명소
TOUR = {
    "amusement_park", "aqueduct", "arch", "bridge", "boardwalk", "botanical_garden",
    "campus", "canyon", "castle", "cathedral", "church_outdoor", "cliff", "coast",
    "courtyard", "creek", "downtown", "fountain", "gazebo_exterior", "harbor",
    "hot_spring", "islet", "lake_natural", "lighthouse", "mountain", "mountain_path",
    "mountain_snowy", "ocean", "pagoda", "palace", "park", "pier", "plaza",
    "promenade", "ruin", "river", "rock_arch", "skyscraper", "temple_asia",
    "tower", "valley", "viaduct", "water_tower", "waterfall", "wind_farm",
    "harbor", "canal_natural", "canal_urban", "field_road", "forest_path",
    "japanese_garden", "kasbah", "lagoon", "landing_deck", "moat_water",
    "oast_house", "orchard", "pond", "raceway", "ski_slope", "snowfield",
    "temple_east_asia", "tundra", "wave", "windmill", "zen_garden",
}

# 맛집: 식당·카페·먹거리 공간 (Food-101 로 보강)
FOOD = {
    "bakery_shop", "bar", "beer_garden", "beer_hall", "cafeteria", "coffee_shop",
    "delicatessen", "diner_outdoor", "fastfood_restaurant", "food_court",
    "ice_cream_parlor", "pub_indoor", "restaurant", "restaurant_kitchen",
    "restaurant_patio", "sushi_bar", "dining_hall", "dining_room", "banquet_hall",
}

# 체험: 액티비티·공방·전통 체험·참여형
ACTIVITY = {
    "arena_performance", "art_studio", "artists_loft", "assembly_line", "ball_pit",
    "bowling_alley", "boxing_ring", "ceramic_workshop", "climbing_gym",
    "amusement_arcade", "dance_studio", "escalator_indoor", "ice_skating_rink_indoor",
    "ice_skating_rink_outdoor", "kindergarten_classroom", "music_studio",
    "natural_history_museum", "pottery_studio", "raft", "recording_studio",
    "science_museum", "stage_indoor", "stage_outdoor", "swimming_pool_outdoor",
    "swimming_pool_indoor", "workshop", "wrestling_ring", "amphitheater",
    "aquarium", "arena_rodeo", "athletic_field_outdoor", "carrousel",
    "escape_room", "greenhouse_indoor", "movie_theater_indoor", "museum_indoor",
    "planetarium_indoor", "water_park",
}

# 쇼핑: 상점·시장·상품 진열
SHOPPING = {
    "bazaar_indoor", "bazaar_outdoor", "bookstore", "candy_store", "clothing_store",
    "department_store", "drugstore", "florist_shop_indoor", "general_store_indoor",
    "general_store_outdoor", "gift_shop", "hardware_store", "jewelry_shop",
    "market_indoor", "market_outdoor", "shoe_shop", "shopfront",
    "shopping_mall_indoor", "supermarket", "toyshop", "delicatessen",
    "pharmacy", "pet_shop", "flea_market_indoor",
}

CATEGORY_SETS = {
    "투어": TOUR,
    "맛집": FOOD,
    "체험": ACTIVITY,
    "쇼핑": SHOPPING,
}


def _norm(name: str) -> str:
    """장면명을 소문자·언더스코어로 통일. 앞의 '/a/' 같은 디렉터리 접두어 제거."""
    key = name.split("/")[-1].strip().lower()
    for ch in (" ", "-", "/"):
        key = key.replace(ch, "_")
    return key


# 비교용으로 세트를 미리 정규화
_NORM_SETS = {cat: {_norm(n) for n in names} for cat, names in CATEGORY_SETS.items()}


def scene_to_category(scene_name: str) -> str | None:
    """Places365 scene name 을 카테고리로. 매핑 없으면 None."""
    key = _norm(scene_name)
    for category, names in _NORM_SETS.items():
        if key in names:
            return category
    return None
