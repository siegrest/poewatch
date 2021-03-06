<?php
/*
 * API endpoint for querying items players have for sale. It returns an
 * aggregated list of every single item, as long as they are tracked by
 * the site. Expected are two GET request parameters: league and account.
 */

/**
 * Ends the execution and displays an error page
 *
 * @param $code int Valid HTTP response code
 * @param $msg string Error message to display
 */
function error($code, $msg)
{
  http_response_code($code);
  die(json_encode(array("error" => $msg)));
}

/**
 * Checks for any possible GET parameter errors
 */
function check_errors()
{
  if (!isset($_GET["league"])) {
    error(400, "Missing league");
  } else if (!isset($_GET["account"])) {
    error(400, "Missing account");
  } else if (strlen($_GET["account"]) > 32) {
    error(400, "Account too long");
  } else if (strlen($_GET["account"]) < 3) {
    error(400, "Account too short");
  } else if (strlen($_GET["league"]) > 64) {
    error(400, "League too long");
  } else if (strlen($_GET["league"]) < 3) {
    error(400, "League too short");
  }
}

/**
 * Queries and formats data from the database
 *
 * @param $pdo PDO Open database connection
 * @param $league string League name, case-insensitive
 * @param $account string Account name, case-insensitive
 * @return array containing the aggregated items and prices
 */
function get_data($pdo, $league, $account)
{
  $query = "select 
    le.id_d as id,
    ifnull(sum(le.stack), 1) + count(*) - 1 as count, 
    date_format(min(le.discovered), '%Y-%m-%dT%TZ') as discovered, 
    date_format(max(le.updated), '%Y-%m-%dT%TZ') as updated, 
    group_concat(round(le.price, 2)) as price,
    group_concat(if(le.price is null, null, ifnull(did1.name, 'Chaos Orb'))) as currency,
    group_concat(if(le.price is null, null, round(ifnull(li.mean, 1) * le.price, 2))) as chaos,
    did.*, dc.name AS category, dg.name AS `group`
  from league_entries as le
  left join data_item_data as did1 on le.id_price = did1.id
  left join league_items as li on le.id_l = li.id_l and le.id_price = li.id_d
  join data_leagues as dl on dl.id = le.id_l
  join data_item_data as did on le.id_d = did.id
  left join data_categories as dc on dc.id = did.id_cat
  left join data_groups as dg on dg.id = did.id_grp
  join league_accounts as la on le.id_a = la.id
  where dl.name = ?
    and la.name = ?
    and le.stash_crc is not null
  group by le.id_d";

  $stmt = $pdo->prepare($query);
  $stmt->execute([$league, $account]);
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

    $tmp = [
      'discovered' => $row['discovered'],
      'updated' => $row['updated'],
      'count' => (int)$row['count'],
      'buyout' => []
    ];

    $tmp = array_merge($itemData, $tmp);

    // If there's a price set
    if ($row['price']) {
      // Split into arrays
      $chaos = explode(',', $row['chaos']);
      $prices = explode(',', $row['price']);
      $currencies = explode(',', $row['currency']);

      // will contain all the prices
      $priceArray = [];
      // will contain only distinct prices
      $distinctArray = [];

      // combine the data
      for ($i = 0; $i < sizeof($prices); $i++) {
        $tmp2 = [
          'price' => (double)$prices[$i],
          'currency' => $currencies[$i],
          'chaos' => (double)$chaos[$i]
        ];

        // Ignore duplicate buyouts
        if (!in_array($tmp2, $distinctArray)) {
          $distinctArray[] = $tmp2;
        }

        $priceArray[] = $tmp2;
      }

      // count how many times the distinct price occurs
      foreach ($distinctArray as $distinctPrice) {
        $count = 0;

        foreach ($priceArray as $price) {
          if ($price === $distinctPrice) $count++;
        }

        $distinctPrice['count'] = $count;
        $tmp['buyout'][] = $distinctPrice;
      }
    }

    $payload[] = $tmp;
  }

  return $payload;
}

header("Content-Type: application/json");
check_errors();
include_once("../details/pdo.php");

$payload = get_data($pdo, $_GET["league"], $_GET["account"]);
echo json_encode($payload, JSON_PRESERVE_ZERO_FRACTION);
