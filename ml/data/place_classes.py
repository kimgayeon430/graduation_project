"""Places365 장면 클래스 → Travel Mission 카테고리 매핑.

Places365 라벨은 데이터셋/미러마다 표기가 다르다.
  - 원본:          "/a/airport_terminal", "/a/arena/performance"
  - 이 프로젝트가 쓰는 미러(ljnlonoljpiljm/places365-256px):
                   "airport terminal", "arena/performance", "market/indoor"
`_norm()` 이 선행 "/x/" 디렉터리를 떼고 공백·하이픈·슬래시를 언더스코어로 통일하므로
어느 표기든 매칭된다. 매핑 세트는 canonical 언더스코어 표기로 작성한다.
"""

# 투어: 관광지·랜드마크·전망·야외 명소·자연
TOUR = {
    "amphitheater", "amusement_park", "aqueduct", "arch", "archaelogical_excavation",
    "art_gallery", "badlands", "bamboo_forest", "beach", "boardwalk", "botanical_garden",
    "bridge", "building_facade", "bullring", "butte", "campus", "canal_natural",
    "canal_urban", "canyon", "castle", "cemetery", "church_outdoor", "cliff", "coast",
    "corn_field", "courtyard", "creek", "crevasse", "dam", "desert_sand",
    "desert_vegetation", "desert_road", "downtown", "field_cultivated", "field_wild",
    "field_road", "forest_broadleaf", "forest_path", "forest_road", "formal_garden",
    "fountain", "glacier", "golf_course", "grotto", "harbor", "hayfield", "hot_spring",
    "iceberg", "islet", "japanese_garden", "kasbah", "lagoon", "lake_natural",
    "lighthouse", "marsh", "medina", "moat_water", "mosque_outdoor", "mountain",
    "mountain_path", "mountain_snowy", "ocean", "orchard", "pagoda", "palace", "park",
    "pasture", "pavilion", "picnic_area", "pier", "plaza", "pond", "promenade",
    "rainforest", "residential_neighborhood", "rice_paddy", "river", "rock_arch",
    "rope_bridge", "ruin", "ski_resort", "ski_slope", "skyscraper", "snowfield",
    "street", "swimming_hole", "synagogue_outdoor", "temple_asia", "topiary_garden",
    "tower", "tree_farm", "tree_house", "tundra", "valley", "viaduct", "village",
    "vineyard", "volcano", "waterfall", "watering_hole", "wave", "wind_farm",
    "windmill", "yard", "zen_garden", "roof_garden", "vegetable_garden",
}

# 맛집: 식당·카페·먹거리 공간 (Food-101 로 보강)
FOOD = {
    "bakery_shop", "bar", "banquet_hall", "beer_garden", "beer_hall", "cafeteria",
    "coffee_shop", "delicatessen", "diner_outdoor", "dining_hall", "dining_room",
    "fastfood_restaurant", "food_court", "ice_cream_parlor", "pizzeria", "pub_indoor",
    "restaurant", "restaurant_kitchen", "restaurant_patio", "sushi_bar", "wet_bar",
}

# 체험: 액티비티·공방·전시 관람·참여형 스포츠
ACTIVITY = {
    "amusement_arcade", "arcade", "aquarium", "arena_hockey", "arena_performance",
    "arena_rodeo", "art_school", "art_studio", "artists_loft", "assembly_line",
    "athletic_field_outdoor", "auditorium", "ball_pit", "ballroom", "baseball_field",
    "basketball_court_indoor", "biology_laboratory", "bowling_alley", "boxing_ring",
    "carrousel", "chemistry_lab", "discotheque", "football_field", "gymnasium_indoor",
    "home_theater", "ice_skating_rink_indoor", "ice_skating_rink_outdoor",
    "kindergarden_classroom", "martial_arts_gym", "movie_theater_indoor", "museum_indoor",
    "museum_outdoor", "music_studio", "natural_history_museum", "orchestra_pit",
    "physics_laboratory", "playground", "playroom", "racecourse", "raceway", "raft",
    "recreation_room", "sandbox", "science_museum", "soccer_field", "stadium_baseball",
    "stadium_football", "stadium_soccer", "stage_indoor", "stage_outdoor",
    "television_studio", "volleyball_court_outdoor", "water_park",
}

# 쇼핑: 상점·시장·상품 진열
SHOPPING = {
    "auto_showroom", "bazaar_indoor", "bazaar_outdoor", "bookstore", "booth_indoor",
    "butchers_shop", "candy_store", "clothing_store", "department_store", "drugstore",
    "fabric_store", "flea_market_indoor", "florist_shop_indoor", "general_store_indoor",
    "general_store_outdoor", "gift_shop", "hardware_store", "jewelry_shop",
    "market_indoor", "market_outdoor", "pet_shop", "pharmacy", "shoe_shop", "shopfront",
    "shopping_mall_indoor", "supermarket", "ticket_booth", "toyshop",
}

CATEGORY_SETS = {
    "투어": TOUR,
    "맛집": FOOD,
    "체험": ACTIVITY,
    "쇼핑": SHOPPING,
}


def _norm(name: str) -> str:
    """장면명을 canonical 언더스코어 표기로. 선행 '/x/' 디렉터리는 제거."""
    key = name.strip().lower()
    if len(key) > 2 and key[0] == "/" and key[2] == "/":
        key = key[3:]
    for ch in (" ", "-", "/"):
        key = key.replace(ch, "_")
    return key


_NORM_SETS = {cat: {_norm(n) for n in names} for cat, names in CATEGORY_SETS.items()}


def canonical_scene(scene_name: str) -> str:
    """장면명을 파일명/그룹키로 쓸 canonical 표기로. (예: '/m/market/indoor' → 'market_indoor')"""
    return _norm(scene_name)


def scene_to_category(scene_name: str) -> str | None:
    """Places365 scene name 을 카테고리로. 매핑 없으면 None."""
    key = _norm(scene_name)
    for category, names in _NORM_SETS.items():
        if key in names:
            return category
    return None
