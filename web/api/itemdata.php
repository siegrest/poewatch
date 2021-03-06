<?php
function get_data($pdo) {
  $query = "SELECT 
    did.id, did.name, did.type, did.frame, did.stack, 
    did.map_tier, did.map_series, did.shaper, did.elder, did.crusader, did.redeemer, did.hunter, did.warlord, did.base_level, 
    did.enchant_min, did.enchant_max ,did.gem_lvl, did.gem_quality, did.gem_corrupted, 
    did.links, did.var, did.icon, 
    dc.name AS category, dg.name AS `group`
  FROM data_item_data AS did 
  LEFT JOIN data_categories AS dc ON dc.id = did.id_cat
  LEFT JOIN data_groups     AS dg ON dg.id = did.id_grp";

  return $pdo->query($query);
}

function parse_data($stmt) {
  $payload = [];

  while ($row = $stmt->fetch()) {
    $itemData = [
      'id'              => (int)  $row['id'],
      'name'            =>        $row['name'],
      'type'            =>        $row['type'],
      'category'        =>        $row['category'],
      'group'           =>        $row['group'],
      'frame'           => (int)  $row['frame'],

      'mapSeries'       =>        $row['map_series']    === null ? null : (int)    $row['map_series'],
      'mapTier'         =>        $row['map_tier']      === null ? null : (int)    $row['map_tier'],
      'influences'      =>        [],
      'baseIsShaper'    =>        $row['shaper']        === null ? null : (bool)   $row['shaper'],
      'baseIsElder'     =>        $row['elder']         === null ? null : (bool)   $row['elder'],
      'baseItemLevel'   =>        $row['base_level']    === null ? null : (int)    $row['base_level'],
      'gemLevel'        =>        $row['gem_lvl']       === null ? null : (int)    $row['gem_lvl'],
      'gemQuality'      =>        $row['gem_quality']   === null ? null : (int)    $row['gem_quality'],
      'gemIsCorrupted'  =>        $row['gem_corrupted'] === null ? null : (bool)   $row['gem_corrupted'],
      'enchantMin'      =>        $row['enchant_min']   === null ? null : (float)  $row['enchant_min'],
      'enchantMax'      =>        $row['enchant_max']   === null ? null : (float)  $row['enchant_max'],
      'stackSize'       =>        $row['stack']         === null ? null : (int)    $row['stack'],
      'linkCount'       =>        $row['links']         === null ? null : (int)    $row['links'],

      'variation'       =>        $row['var'],
      'icon'            =>        $row['icon']
    ];

    // Add false fields
    if ($itemData['category'] === 'base') {
      if (!$itemData['baseIsShaper']) {
        $itemData['baseIsShaper'] = false;
      }

      if (!$itemData['baseIsElder']) {
        $itemData['baseIsElder'] = false;
      }
    }

    foreach (['shaper', 'elder', 'crusader', 'redeemer', 'hunter', 'warlord'] as $influence) {
      if ($row[$influence]) {
        $itemData['influences'][] = $influence;
      }
    }

    // Filter out null values
    $itemData = array_filter($itemData, function($value) {
      return $value !== null;
    });

    $payload[] = $itemData;
  }

  return $payload;
}

header("Content-Type: application/json");
include_once ( "../details/pdo.php" );

$payload = parse_data(get_data($pdo));
echo json_encode($payload);
