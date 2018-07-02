<?php

require_once 'vendor/paragonie/sodium_compat/autoload.php';
require_once 'vendor/mustangostang/spyc/Spyc.php';
require_once 'vendor/autoload.php';
use MessagePack\BufferUnpacker;
use MessagePack\Exception\UnpackingFailedException;

$publicKey = sodium_hex2bin('8593a07f70809c0adc0c72e16c2a958997419bdc428fe1eb46f58e59ac2e53d0');

$unpacker = new BufferUnpacker();
$unpacker->reset(file_get_contents("php://input"));
$post = [];
try {
    $post = $unpacker->unpack();
} catch (UnpackingFailedException $e) {
    http_response_code(400);
    die;
}

if (!file_exists('metadata.yml') )
    $oldMetadata = [];
else
    $oldMetadata = Spyc::YAMLLoad('metadata.yml');
$signature = $post['signature'];
$verified = sodium_crypto_sign_verify_detached($signature, $post['metadata'],
    $publicKey);
if (!$verified) {
    http_response_code(403);
    die;
}
$newMetadata = Spyc::YAMLLoadString($post['metadata']);
$timetables = $post['timetables'];
foreach ($timetables as $id => $timetable) {
    $t = $timetable['t'];
    $sha = $timetable['sha'];
    $checksum = hash('sha256', $t);
    if ($checksum != $sha) {
        http_response_code(400);
        die("checksums invalid for $id, expected $sha got $checksum");
    }
}

$oldIDs = [];
$newIDs = [];
foreach ($oldMetadata as $it) {
    array_push($oldIDs, $it['id']);
}
foreach ($newMetadata as $it) {
    array_push($newIDs, $it['id']);
}
$toDelete = array_diff($oldIDs, $newIDs);
foreach ($toDelete as $it) {
    unlink("$it.db.gz");
}
foreach ($timetables as $id => $timetable) {
    file_put_contents("$id.db.gz", $timetable);
}

file_put_contents('metadata.yml', $post['metadata']);
?>
