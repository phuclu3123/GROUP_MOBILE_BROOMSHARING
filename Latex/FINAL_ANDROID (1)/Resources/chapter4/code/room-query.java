        Query query =
                db.collection("rooms")
                        .whereEqualTo("approvalStatus", "APPROVED")
                        .whereEqualTo("listingStatus", "ACTIVE")
                        .orderBy(FieldPath.documentId())
                        .limit(PAGE_SIZE);
        if (after != null) query = query.startAfter(after);
        query.get(Source.SERVER)
