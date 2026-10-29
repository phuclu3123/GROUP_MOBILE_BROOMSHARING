"""Convert the reviewed local inventory to FireDraw's nodes/edges import format.

No Firebase connection or real user values are used. Run from the project root:
    python tools/visualization/export_firedraw.py
"""

import json
import copy
import re
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DESIGN = ROOT / "docs" / "design"
SOURCE = DESIGN / "firestore-visualization-schema.json"
TARGET = DESIGN / "broomsharing-firedraw.json"
ALLOWED_TYPES = {
    "string", "number", "boolean", "timestamp", "array", "map", "reference", "geopoint"
}


def identifier(prefix, path):
    return prefix + "-" + re.sub(r"[^A-Za-z0-9_-]+", "-", path).strip("-")


def field_type(original):
    if original.startswith("array"):
        return "array"
    if original.startswith("map"):
        return "map"
    value = original.split("/")[0]
    if value in {"integer", "double", "number"}:
        return "number"
    # FireDraw's selector has no unknown/any/null type. Keep uncertainty in
    # schemaType/typeUnconfirmed instead of inventing evidence for the schema.
    if value not in ALLOWED_TYPES:
        return "string"
    return value


def make_field(item):
    result = {
        "name": item["name"],
        "type": field_type(item["type"]),
        "description": item["notes"],
        "schemaType": item["type"],
        "typeUnconfirmed": "unknown" in item["type"] or "any" in item["type"],
    }
    if result["type"] in {"array", "map"}:
        result["fields"] = []
    if "null" in item["type"]:
        result["nullable"] = True
    return result


def nested_fields(items):
    """One node field per stored field; flattened inventory rows become children."""
    result = []
    by_name = {}
    for item in items:
        name = item["name"]
        if "." not in name:
            field = make_field(item)
            by_name[name] = field
            result.append(field)
            continue
        parent_name, child_name = name.split(".", 1)
        array_parent = parent_name.endswith("[]")
        parent = by_name[parent_name.removesuffix("[]")]
        if array_parent:
            if not parent["fields"]:
                parent["fields"].append({"name": "item", "type": "map", "fields": []})
            parent = parent["fields"][0]
        child = make_field({**item, "name": child_name})
        parent["fields"].append(child)
    # FireDraw arrays expose their element schema through fields[]. The item
    # below is a schema descriptor, never a sample persisted value or field.
    for field in result:
        if field["type"] == "array" and not field["fields"]:
            match = re.fullmatch(r"array<(.+)>", field["schemaType"])
            if match:
                field["fields"].append(make_field({
                    "name": "item", "type": match.group(1),
                    "notes": "Array element schema; not an extra stored field."
                }))
    # Dynamic hourlyBookings.{bookingId} is itself an array of integer slots.
    for field in result:
        if field["type"] == "map":
            for child in field["fields"]:
                if child["type"] == "array" and not child["fields"]:
                    child["fields"] = [{"name": "item", "type": "number", "schemaType": "integer"}]
    return result


def height(fields):
    return 120 + sum(135 + (height(f["fields"]) - 120 if f.get("fields") else 0) for f in fields)


def main():
    schema = json.loads(SOURCE.read_text(encoding="utf-8"))
    nodes = [{
        "id": "database", "type": "database", "position": {"x": 0, "y": 0},
        "data": {"label": "broomsharing", "width": 300, "height": 160,
                 "databaseName": schema["database"], "description": schema["scope"]},
    }]
    edges = []
    docs = schema["documents"]
    doc_nodes = {}
    collection_nodes = {}
    fields_by_path = {}
    # Layout is applied as an ownership tree after every node has been built.
    for index, document in enumerate(docs):
        path = document["path"]
        parts = path.split("/")
        x, y = 0, 0
        cid, did = identifier("collection", path), identifier("document", path)
        doc_nodes[path], collection_nodes[path] = did, cid
        fields = nested_fields(document["fields"])
        fields_by_path[path] = fields
        h = height(fields) + 160
        parent_path = "/".join(parts[:-2]) if len(parts) > 2 else None
        parent_id = identifier("document", parent_path) if parent_path else "database"
        rules_only = document["status"] == "rules_only"
        color = "#64748b" if rules_only else "#14b8a6"
        common = {"schemaPath": path, "schemaStatus": document["status"],
                  "description": "Rules-only; incomplete schema" if rules_only else path}
        nodes.append({
            "id": cid, "type": "collection", "position": {"x": x, "y": y},
            "data": {"id": cid, "label": parts[-2] + (" [S-only]" if rules_only else ""), "isSubcollection": bool(parent_path),
                     "parentDocumentId": parent_id if parent_path else None,
                     "height": 150, "width": 300, "fromFirebase": False, **common},
            "style": {"borderColor": color},
        })
        nodes.append({
            "id": did, "type": "document", "position": {"x": x + 420, "y": y},
            "data": {"id": did, "label": parts[-1] + (" [S-only]" if rules_only else ""), "parentDocumentId": cid,
                     "fields": fields, "subcollections": [], "width": 900, "height": h,
                     **common},
            "style": {"width": 900, "borderColor": color},
        })
        for source, target in [(parent_id, cid), (cid, did)]:
            edges.append({"id": f"contains-{source}-{target}", "source": source,
                          "target": target, "type": "smoothstep",
                          "data": {"kind": "containment"},
                          "style": {"stroke": "#94a3b8", "strokeWidth": 1.5}})

    # Make child collection IDs discoverable in document data, too.
    by_id = {n["id"]: n for n in nodes}
    for path, cid in collection_nodes.items():
        parent = by_id[cid]["data"].get("parentDocumentId")
        if parent:
            by_id[parent]["data"]["subcollections"].append({"id": cid, "name": path.split("/")[-2]})

    def resolve(text):
        first = text.split("/", 1)[0].split(".", 1)[0]
        if first == "favorites":
            return doc_nodes["users/{uid}/favorites/{roomId}"]
        if first == "messages":
            return doc_nodes["conversations/{guestUid:hostUid}/messages/{messageId}"]
        if text.startswith("conversations/{guestUid:hostUid}/messages/"):
            return doc_nodes["conversations/{guestUid:hostUid}/messages/{messageId}"]
        for path, did in doc_nodes.items():
            if path.split("/")[0] == first and len(path.split("/")) == 2:
                return did
        return None

    metadata_only = []
    for index, rel in enumerate(schema["relationships"]):
        if "chứa" in rel["source"]:
            continue  # Already represented by the owning document -> collection edge.
        source, target = resolve(rel["source"]), resolve(rel["target"])
        if not source or not target:
            metadata_only.append(rel)
            continue
        edges.append({
            "id": f"logical-{index:02d}", "source": source, "target": target,
            "type": "smoothstep", "label": rel["source"] + " | " + rel["cardinalityAndNotes"],
            "style": {"stroke": "#60a5fa", "strokeWidth": 1, "strokeDasharray": "5 4"},
            "data": {"kind": "logical_string_id", **rel},
        })

    # Validate the public importer filters and all graph endpoints locally.
    assert len(nodes) == 37
    assert len({n["id"] for n in nodes}) == len(nodes)
    assert all(n["id"] and n["type"] in {"database", "collection", "document", "subcollection"}
               and n["data"] and n["position"] for n in nodes)
    assert all(e["id"] and e["source"] in by_id and e["target"] in by_id
               and e["type"] == "smoothstep" for e in edges)
    def validate_fields(fields):
        for field in fields:
            assert field["type"] in ALLOWED_TYPES
            if "fields" in field:
                validate_fields(field["fields"])
    for fields in fields_by_path.values():
        validate_fields(fields)

    # Preserve the full authoritative source in metadata: unsupported numeric
    # distinctions, uncertain types, external services and dynamic relationships
    # must not be lost in the visual editor's narrower field-type vocabulary.
    output = {
        "nodes": nodes, "edges": edges, "viewport": {"x": 80, "y": 80, "zoom": 0.45},
        "metadata": {
            "exportedAt": datetime.now(timezone.utc).isoformat(), "version": "1.1",
            "exportedBy": "BroomSharing schema converter for FireDraw",
            "totalNodes": len(nodes), "totalEdges": len(edges),
            "scope": schema["scope"], "schemaInventory": schema,
            "metadataOnlyRelationships": metadata_only,
            "typeMapping": "integer/double -> number; unknown/any -> string display placeholder only, original type preserved in schemaType and metadata",
            "compatibilitySource": "FireDraw public frontend bundles checked 2026-10-23; importer requires nodes/edges and smoothstep edges",
            "compatibilityStatus": "Validated against public importer predicates and field renderer structure; not imported into an authenticated user canvas by this script.",
        },
    }
    # The previous all-links canvas was unreadable. Keep logical relationships
    # in metadata; the complete field dictionary shows ownership edges only.
    output["metadata"]["logicalEdges"] = [e for e in edges if e["data"]["kind"] != "containment"]
    output["edges"] = [e for e in edges if e["data"]["kind"] == "containment"]

    def arrange_tree(canvas):
        lookup = {n["id"]: n for n in canvas["nodes"]}
        children = {}
        for edge in canvas["edges"]:
            children.setdefault(edge["source"], []).append(edge["target"])
        sizes = {}
        def measure(node_id):
            node = lookup[node_id]
            own_height = node["data"].get("height", 180)
            subs = children.get(node_id, [])
            sizes[node_id] = max(own_height, sum(measure(c) for c in subs) + max(0, len(subs)-1)*240)
            return sizes[node_id]
        measure("database")
        def place(node_id, x, y):
            node = lookup[node_id]
            node["position"] = {"x": x, "y": y}
            width = node["data"].get("width", 300)
            child_y = y
            for child in children.get(node_id, []):
                place(child, x + width + 220, child_y)
                child_y += sizes[child] + 240
        place("database", 0, 0)
        canvas["metadata"]["totalNodes"] = len(canvas["nodes"])
        canvas["metadata"]["totalEdges"] = len(canvas["edges"])
        canvas["metadata"]["layout"] = "Left-to-right ownership tree; full fields; logical ID relationships retained in metadata, not drawn over field cards."
        # Bounding boxes use conservative editor height estimates.
        rectangles = [(n["id"], n["position"]["x"], n["position"]["y"],
                       n["data"].get("width",300), n["data"].get("height",180)) for n in canvas["nodes"]]
        for i, (aid, ax, ay, aw, ah) in enumerate(rectangles):
            for bid, bx, by, bw, bh in rectangles[i+1:]:
                assert ax+aw<=bx or bx+bw<=ax or ay+ah<=by or by+bh<=ay, (aid,bid)

    def save(canvas, path):
        path.write_text(json.dumps(canvas, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")

    arrange_tree(output)
    save(output, TARGET)
    folder = DESIGN / "firedraw"
    folder.mkdir(exist_ok=True)
    groups = {
        "01-accounts": {"users", "publicHosts", "usernames", "hostApplications"},
        "02-booking": {"buildings", "rooms", "bookings", "availabilityMonths"},
        "03-chat": {"conversations"},
        "04-administration-reference": {"propertyVerifications", "reports", "auditLogs", "rentalPlaces", "payments", "notifications"},
    }
    covered = set()
    for filename, roots in groups.items():
        subset = copy.deepcopy(output)
        subset["nodes"] = [n for n in subset["nodes"] if n["id"]=="database" or n["data"]["schemaPath"].split("/")[0] in roots]
        ids = {n["id"] for n in subset["nodes"]}
        subset["edges"] = [e for e in subset["edges"] if e["source"] in ids and e["target"] in ids]
        subset["metadata"]["view"] = filename
        subset["metadata"]["partialView"] = True
        covered.update(n["data"]["schemaPath"] for n in subset["nodes"] if n["type"]=="document")
        arrange_tree(subset)
        save(subset, folder / (filename+".json"))
    assert covered == set(doc_nodes)

    # The overview preserves the real alternating ownership tree as well.
    # Full logical references are retained in metadata and drawn in the native
    # draw.io companion, whose renderer can represent uncertainty faithfully.
    overview = copy.deepcopy(output)
    overview["metadata"]["view"] = "00-storage-overview"
    overview["metadata"]["fieldScope"] = "Overview only; full fields in grouped dictionaries."
    overview["metadata"]["logicalRelationshipCompanion"] = "../drawio/field-and-relationship-dictionary.md"
    for n in overview["nodes"]:
        if n["type"] == "document":
            n["data"]["fields"] = []
            n["data"]["height"] = 280
    arrange_tree(overview)
    save(overview, folder / "00-relationships-overview.json")
    print(f"Created full tree: {len(nodes)} nodes, {len(output['edges'])} ownership edges; 18 document patterns.")
    print("Created four full-field groups and one storage overview. Complete relationship specifications: Markdown companion.")


if __name__ == "__main__":
    main()
