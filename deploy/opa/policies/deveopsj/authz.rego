package deveopsj.authz

default allow := false

allow if {
    input.resource.type == "spending"
    input.subject.member_id == input.resource.owner_id
    input.subject.role in {"USER", "ADMIN"}
    input.action in {"update", "delete"}
}
