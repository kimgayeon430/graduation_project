"""Places365 장면 클래스 → Travel Mission 카테고리 매핑.

Places365 라벨은 "/a/airport_terminal" 처럼 앞에 알파벳 디렉터리가 붙는다.
여기서는 뒷부분(scene name)만 키로 쓴다. 필요에 따라 목록을 넓혀 가며 조정하라.
"""

# 투어: 관광지·랜드마크·전망·야외 명소
TOUR = {
    "amusement_park", "aqueduct", "arch", "bridge", "boardwalk", "botanical_garden",
    "campus", "canyon", "castle", "cathedral", "church-outdoor", "cliff", "coast",
    "courtyard", "creek", "downtown", "fountain", "gazebo-exterior", "harbor",
    "hot_spring", "islet", "lake-natural", "lighthouse", "mountain", "mountain_path",
    "mountain_snowy", "ocean", "pagoda", "palace", "park", "pier", "plaza",
    "promenade", "ruin", "river", "rock_arch", "skyscraper", "temple-asia",
    "tower", "valley", "viaduct", "water_tower", "waterfall", "wind_farm",
}

# 맛집: 식당·카페·먹거리 공간 (Food-101 로 보강)
FOOD = {
    "bakery-shop", "bar", "beer_garden", "beer_hall", "cafeteria", "coffee_shop",
    "delicatessen", "diner-outdoor", "fastfood_restaurant", "food_court",
    "ice_cream_parlor", "pub-indoor", "restaurant", "restaurant_kitchen",
    "restaurant_patio", "sushi_bar",
}

# 체험: 액티비티·공방·전통 체험·참여형
ACTIVITY = {
    "arena-performance", "art_studio", "artists_loft", "assembly_line", "ball_pit",
    "bowling_alley", "boxing_ring", "ceramic_workshop", "climbing_gym",
    "amusement_arcade", "dance_studio", "escalator-indoor", "ice_skating_rink-indoor",
    "kindergarten_classroom", "music_studio", "natural_history_museum",
    "pottery_studio", "raft", "recording_studio", "science_museum", "ski_slope",
    "stage-indoor", "swimming_pool-outdoor", "workshop", "wrestling_ring",
}

# 쇼핑: 상점·시장·상품 진열
SHOPPING = {
    "bazaar-indoor", "bazaar-outdoor", "bookstore", "candy_store", "clothing_store",
    "department_store", "drugstore", "florist_shop-indoor", "general_store-indoor",
    "general_store-outdoor", "gift_shop", "hardware_store", "jewelry_shop", "market-indoor",
    "market-outdoor", "shoe_shop", "shopfront", "shopping_mall-indoor", "supermarket",
    "toyshop",
}

CATEGORY_SETS = {
    "투어": TOUR,
    "맛집": FOOD,
    "체험": ACTIVITY,
    "쇼핑": SHOPPING,
}


def scene_to_category(scene_name: str) -> str | None:
    """Places365 scene name 을 카테고리로. 매핑 없으면 None."""
    key = scene_name.split("/")[-1]
    for category, names in CATEGORY_SETS.items():
        if key in names:
            return category
    return None
