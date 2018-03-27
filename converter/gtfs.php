<?php

require 'vendor/mustangostang/spyc/Spyc.php';

$metadata = Spyc::YAMLLoad('metadata.yml');
$today = date('Ymd');
$current = '';
$sizeU = '';
$sizeC = '';

foreach($metadata as $row) {
    $start = $row['start'];
    $end = $row['end'];
    if ($start <= $today and $today <= $end) {
        $current = $row['id'];
        $sizeU = $row['size_uncompressed'];
        $sizeC = $row['size_compressed'];
        break;
    }
}
unset($row);

$etag = $_SERVER['HTTP_ETAG'];

if ($etag == $current) {
    http_response_code(304);
} else {
    header("ETag: $current");
    header('Content-Type: application/octet-stream');
    header('Content-Disposition: attachment; filename="timetable.db.gz"');
    header('Content-Length: ' . filesize("$current.db.gz"));
    header('X-Uncompressed-Content-Length: ' . $sizeU);
    readfile("$current.db.gz");
}
?>
