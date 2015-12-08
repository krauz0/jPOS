/*
 * jPOS Project [http://jpos.org]
 * Copyright (C) 2000-2015 Alejandro P. Revilla
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.jpos.security;

/**
 * Key usages types used by key pair generators.
 *
 * @author Piotr Krauzowicz
 */
public enum KeyUsage {

    /**
     * Signature only.
     */
    SIGNATURE(0),

    /**
     * Key management only.
     */
    KEY_MANAGEMENT(1),

    /**
     * Both signature and key management.
     */
    BOTH(2),

    /**
     * Integrated Chip Card (ICC) Key.
     */
    ICC(3),

    /**
     * Allows general purpose decryption of data (e&#46;g&#46; TLS/SSL premaster secret).
     */
    GENERAL_PURPOSE(4);

    private final int code;

    private KeyUsage(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

}
