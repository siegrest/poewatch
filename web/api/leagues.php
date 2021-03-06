<?php
function get_data($pdo) {
  $query = "SELECT * FROM data_leagues WHERE active = 1";
  $stmt = $pdo->query($query);
  return $stmt;
}

function parse_data($stmt) {
  $payload = array();

  while ($row = $stmt->fetch()) {
    $tmp = array(
      'id'        => (int)  $row['id'],
      'name'      =>        $row['name'],
      'display'   =>        $row['display'],
      'hardcore'  => (bool) $row['hardcore'],
      'upcoming'  => (bool) $row['upcoming'],
      'active'    => (bool) $row['active'],
      'event'     => (bool) $row['event'],
      'challenge' => (bool) $row['challenge'],
      'start'     =>        $row['start'],
      'end'       =>        $row['end']
    );

    $payload[] = $tmp;
  }

  return $payload;
}

// Define content type
header('Content-Type: application/json');

// Connect to database
include_once ( '../details/pdo.php' );

// Get data from database
$stmt = get_data($pdo);

// Parse received data
$payload = parse_data($stmt);

// Display generated data
echo json_encode($payload);
