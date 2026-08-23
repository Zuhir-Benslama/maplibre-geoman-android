package com.geoman.maplibre.geoman.core.features

/**
 * Parent-child feature registry (web parity: FeatureData.parent/children).
 *
 * Stores directed child -> parent links with a reverse index for fast
 * descendant walks. Linking may not create cycles; callers must validate
 * feature existence and acyclicity before calling [link].
 *
 * Thread safety: instances are guarded by the owning [Features] monitor —
 * every access must happen inside a `synchronized(features)` block.
 */
internal class FeatureRelationships {

    private val childToParent = HashMap<String, String>()
    private val parentToChildren = HashMap<String, MutableSet<String>>()

    /**
     * Register [childId] as a child of [parentId], replacing any previous
     * parent of the child.
     */
    fun link(childId: String, parentId: String) {
        detach(childId)
        childToParent[childId] = parentId
        parentToChildren.getOrPut(parentId) { mutableSetOf() }.add(childId)
    }

    /** Clear the parent link of [childId]; no-op when unlinked. */
    fun detach(childId: String) {
        val previousParent = childToParent.remove(childId) ?: return
        parentToChildren[previousParent]?.let { children ->
            children.remove(childId)
            if (children.isEmpty()) parentToChildren.remove(previousParent)
        }
    }

    /** Drop every link. */
    fun clear() {
        childToParent.clear()
        parentToChildren.clear()
    }

    /** The registered parent of [featureId], or null when it has none. */
    fun parentIdOf(featureId: String): String? = childToParent[featureId]

    /** Direct children of [parentId]; empty when it has none. */
    fun childrenOf(parentId: String): Set<String> = parentToChildren[parentId]?.toSet() ?: emptySet()

    /** All descendants of [parentId], breadth-first, excluding the parent itself. */
    fun descendantsOf(parentId: String): Set<String> {
        val descendants = mutableSetOf<String>()
        val queue = ArrayDeque(parentToChildren[parentId].orEmpty())
        while (queue.isNotEmpty()) {
            val childId = queue.removeFirst()
            if (descendants.add(childId)) {
                queue.addAll(parentToChildren[childId].orEmpty())
            }
        }
        return descendants
    }

    /** True when [candidateAncestor] is [featureId] itself or a transitive parent. */
    fun isAncestorOrSelf(candidateAncestor: String, featureId: String): Boolean {
        var current: String? = featureId
        while (current != null) {
            if (current == candidateAncestor) return true
            current = childToParent[current]
        }
        return false
    }
}
