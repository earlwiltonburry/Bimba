<?php

set_time_limit(0);

require_once 'vendor/paragonie/sodium_compat/autoload.php';
require_once 'vendor/mustangostang/spyc/Spyc.php';
require_once 'vendor/autoload.php';
use MessagePack\BufferUnpacker;
use MessagePack\Exception\UnpackingFailedException;

$publicKey = sodium_hex2bin('8593a07f70809c0adc0c72e16c2a958997419bdc428fe1eb46f58e59ac2e53d0');

if ($_SERVER['REQUEST_METHOD'] === 'PUT') {
    ob_start();
    $handle = fopen('php://input', 'rb');
    $length = trim(fgets($handle));
    $data = fread($handle, $length);

    $unpacker = new BufferUnpacker();
    $unpacker->reset($data);
    $post = [];
    try {
        $post = $unpacker->unpack();
    } catch (UnpackingFailedException $e) {
        http_response_code(400);
        die;
    }

    if (!file_exists('metadata.yml') )
        $metadata = [];
    else
        $metadata = Spyc::YAMLLoad('metadata.yml');
    $signature = $post['signature'];
    $meta = $post['meta'];
    $id = $meta['id'];
    
    $output = fopen("$id.db.gz", 'wb');
    stream_copy_to_stream($handle, $output);
    fclose($output);
    fclose($handle);

    $sha = hash_file('sha256', "$id.db.gz");
    $verified = sodium_crypto_sign_verify_detached($signature, $sha, $publicKey);
    if (!$verified) {
        http_response_code(403);
        unlink("$id.db.gz");
        die;
    }

    $metadata[] = $meta;

    file_put_contents('metadata.yml', Spyc::YAMLDump($metadata, false, 0, true));
    ob_end_flush();
    ob_flush();
    flush();
} elseif ($_SERVER['REQUEST_METHOD'] === 'DELETE') {
    ob_start();
    $req = explode(':', substr(@$_SERVER['PATH_INFO'], 1), 2);
    $id = $req[0];
    $sig = base64_decode($req[1]);
    if ($id == '') {
        http_response_code(400);
        die('no id in DELETE');
    }
    if (preg_match('/[0-9a-f]{64}/', $id, $matches) === 0) {
        http_response_code(400);
        die('wrong id in DELETE');
    }
    if ($matches[0] != $id) {
        http_response_code(400);
        die('wrong id in DELETE');
    }
    $verified = sodium_crypto_sign_verify_detached($sig, $id, $publicKey);
    if (!$verified) {
        http_response_code(403);
        die('unverified DELETE');
    }

    if (!file_exists('metadata.yml') )
        $metadata = [];
    else
        $metadata = Spyc::YAMLLoad('metadata.yml');

    $newMetadata = [];
    foreach ($metadata as $it) {
        if ($it['id'] != $id)
            $newMetadata[] = $it;
    }
    file_put_contents('metadata.yml', Spyc::YAMLDump($newMetadata, false, 0, true));
    unlink("$id.db.gz");
    ob_end_flush();
    ob_flush();
    flush();
}
?>
