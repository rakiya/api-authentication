package habanero.requests

import io.konform.validation.Validation
import io.konform.validation.jsonschema.maxLength
import io.konform.validation.jsonschema.minLength
import io.konform.validation.jsonschema.pattern

data class PostAccountRequest(val email: String, val screenName: String, val password: String) {
    companion object {
        val validate: Validation<PostAccountRequest> = Validation {
            PostAccountRequest::email required {
                maxLength(255) hint "255文字以下で入力してください"
                pattern("[^\\s]+@[^\\s]+") hint "形式が違います"
            }
            PostAccountRequest::screenName required {
                minLength(1) hint "1文字以上で入力してください"
                maxLength(32) hint "255文字以下で入力してください"
            }
            PostAccountRequest::password required {
                minLength(6) hint "6文字以上1024文字以下で入力してください"
                maxLength(1024) hint "6文字以上1024文字以下で入力してください"
                pattern("^\\p{ASCII}+$") hint "半角英数字と記号のみ利用できます"
                pattern(".*[A-Z].*") hint "大文字を1文字以上お使いください"
                pattern(".*[\\x21-\\x2f\\x3a-\\x40\\x5b-\\x60\\x7b-\\x7e].*") hint "記号を1文字以上お使いください"
            }
        }
    }
}