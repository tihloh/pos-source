package com.tihloh.pos.philsys

import org.json.JSONObject

data class PhilSysProfile(
    val firstName: String,
    val middleName: String?,
    val lastName: String,
    val suffix: String?,
    val sex: String?,
    val dateOfBirth: String?,
    val placeOfBirth: String?,
    val dateIssued: String?,
    val issuer: String?,
    val algorithm: String?
) {
    val fullName: String
        get() = listOfNotNull(
            firstName.takeIf { it.isNotBlank() },
            middleName?.takeIf { it.isNotBlank() },
            lastName.takeIf { it.isNotBlank() },
            suffix?.takeIf { it.isNotBlank() }
        ).joinToString(" ")
}

sealed class PhilSysParseResult {
    data class Success(val profile: PhilSysProfile) : PhilSysParseResult()
    data class Invalid(val message: String) : PhilSysParseResult()
}

object PhilSysQrParser {
    fun parse(raw: String): PhilSysParseResult {
        val text = raw.trim()
        if (!text.startsWith("{")) {
            return PhilSysParseResult.Invalid("This QR is not a PhilSys JSON payload.")
        }

        return runCatching {
            val root = JSONObject(text)
            val subject = root.optJSONObject("subject")
                ?: return PhilSysParseResult.Invalid("PhilSys subject data was not found.")

            val issuer = root.optString("Issuer").trim().ifBlank { null }
            val alg = root.optString("alg").trim().ifBlank { null }
            val signature = root.optString("signature").trim()

            val first = subject.optString("fName").trim()
            val last = subject.optString("lName").trim()
            if (first.isBlank() || last.isBlank()) {
                return PhilSysParseResult.Invalid("Required PhilSys name fields are missing.")
            }

            // PSA's documented PhilID QR structure includes issuer, algorithm,
            // subject and a digital signature. Presence is used only for format
            // detection here; this parser does not claim cryptographic verification.
            if (!issuer.equals("PSA", ignoreCase = true) ||
                alg.isNullOrBlank() ||
                signature.isBlank()
            ) {
                return PhilSysParseResult.Invalid(
                    "The QR does not match the expected PhilSys signed-data structure."
                )
            }

            PhilSysParseResult.Success(
                PhilSysProfile(
                    firstName = first,
                    middleName = subject.optString("mName").trim().ifBlank { null },
                    lastName = last,
                    suffix = subject.optString("Suffix").trim().ifBlank { null },
                    sex = subject.optString("sex").trim().ifBlank { null },
                    dateOfBirth = subject.optString("DOB").trim().ifBlank { null },
                    placeOfBirth = subject.optString("POB").trim().ifBlank { null },
                    dateIssued = root.optString("DateIssued").trim().ifBlank { null },
                    issuer = issuer,
                    algorithm = alg
                )
            )
        }.getOrElse {
            PhilSysParseResult.Invalid("Unable to read the PhilSys QR data.")
        }
    }
}
