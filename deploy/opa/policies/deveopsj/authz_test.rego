package deveopsj.authz_test

import data.deveopsj.authz.allow

test_owner_can_update_spending if {
    allow with input as {
        "subject": {"member_id": 7, "role": "USER"},
        "action": "update",
        "resource": {"type": "spending", "id": 10, "owner_id": 7}
    }
}

test_other_member_cannot_delete_spending if {
    not allow with input as {
        "subject": {"member_id": 8, "role": "USER"},
        "action": "delete",
        "resource": {"type": "spending", "id": 10, "owner_id": 7}
    }
}

test_unknown_action_is_denied if {
    not allow with input as {
        "subject": {"member_id": 7, "role": "USER"},
        "action": "approve",
        "resource": {"type": "spending", "id": 10, "owner_id": 7}
    }
}
