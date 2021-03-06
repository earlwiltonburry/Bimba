<?php

/*
 * This file is part of the rybakit/msgpack.php package.
 *
 * (c) Eugene Leonovich <gen.work@gmail.com>
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

namespace MessagePack\TypeTransformer;

use MessagePack\Packer;
use MessagePack\Type\Binary;

class BinaryTransformer implements Packable
{
    public function pack(Packer $packer, $value) : ?string
    {
        return $value instanceof Binary
            ? $packer->packBin($value->data)
            : null;
    }
}
