import { useState, useEffect, memo } from 'react';
import useUmlStore from '../store/useUmlStore.js';
import wsClient from '../services/wsClient.js';

function UmlClassCard({ cls, onMouseDown }) {
  const {
    deleteClass,
    updateClass,
    updateAttribute,
    addAttribute,
    removeAttribute,
    addMethod,
    updateMethod,
    removeMethod,
    locks,
    requestLock,
    releaseLock,
  } = useUmlStore();
  const [isDragging, setIsDragging] = useState(false);

  // Estados locales para edición reactiva
  const [isEditingName, setIsEditingName] = useState(false);
  const [classNameInput, setClassNameInput] = useState(cls.name);
  const [editingAttrIndex, setEditingAttrIndex] = useState(null);
  const [attrInputs, setAttrInputs] = useState(cls.attrs || []);
  const [editingMethodIndex, setEditingMethodIndex] = useState(null);
  const [methodInputs, setMethodInputs] = useState(cls.methods || []);

  useEffect(() => {
    setClassNameInput(cls.name);
    setAttrInputs(cls.attrs || []);
    setMethodInputs(cls.methods || []);
  }, [cls.name, cls.attrs, cls.methods]);

  // Verificar estado de bloqueo del elemento de clase
  const classLock = locks[cls.id];
  const isLockedByOther = classLock && classLock.locked && classLock.lockedByUserId !== wsClient.userId;
  const isLockedByMe = classLock && classLock.locked && classLock.lockedByUserId === wsClient.userId;

  function handleMouseDown(e) {
    if (isLockedByOther) return; // Impedir drag si otro usuario tiene el bloqueo
    requestLock(cls.id, 'CLASS');
    setIsDragging(true);
    onMouseDown(e);
    window.addEventListener('mouseup', () => setIsDragging(false), { once: true });
  }

  // --- Handlers de Bloqueo y Edición de Nombre ---
  function startEditingName() {
    if (isLockedByOther) return;
    setIsEditingName(true);
    requestLock(cls.id, 'CLASS');
  }

  function finishEditingName() {
    setIsEditingName(false);
    const trimmed = classNameInput.trim();
    if (trimmed && trimmed !== cls.name) {
      updateClass(cls.id, { name: trimmed });
    }
    releaseLock(cls.id, 'CLASS');
  }

  // --- Handlers de Bloqueo y Edición de Atributos ---
  function startEditingAttrName(index) {
    if (isLockedByOther) return;
    const attrElementId = `${cls.id}_attr_${index}`;
    const attrLock = locks[attrElementId];
    if (attrLock && attrLock.locked && attrLock.lockedByUserId !== wsClient.userId) {
      return;
    }
    setEditingAttrIndex(index);
    requestLock(attrElementId, 'ATTR');
  }

  function finishEditingAttrName(index) {
    const attrElementId = `${cls.id}_attr_${index}`;
    setEditingAttrIndex(null);
    const current = attrInputs[index] || cls.attrs?.[index];
    if (current) {
      const trimmedName = (current.name || '').trim();
      if (trimmedName && trimmedName !== cls.attrs?.[index]?.name) {
        updateAttribute(cls.id, index, { ...current, name: trimmedName });
      }
    }
    releaseLock(attrElementId, 'ATTR');
  }

  function finishEditingAttrType(index) {
    const attrElementId = `${cls.id}_attr_${index}`;
    const current = attrInputs[index] || cls.attrs?.[index];
    if (current) {
      const trimmedType = (current.type != null ? String(current.type) : 'String').trim() || 'String';
      if (trimmedType !== cls.attrs?.[index]?.type) {
        updateAttribute(cls.id, index, { ...current, type: trimmedType });
      }
    }
    releaseLock(attrElementId, 'ATTR');
  }

  function handleAddAttr(e) {
    e.stopPropagation();
    if (isLockedByOther) return;
    const newAttr = {
      name: `campo_${(cls.attrs?.length || 0) + 1}`,
      type: 'String',
      version: 0,
    };
    addAttribute(cls.id, newAttr);
  }

  function handleRemoveAttr(e, index, attrName) {
    e.stopPropagation();
    if (isLockedByOther) return;
    const attrElementId = `${cls.id}_attr_${index}`;
    if (locks[attrElementId]?.locked && locks[attrElementId]?.lockedByUserId !== wsClient.userId) {
      return;
    }
    removeAttribute(cls.id, index, attrName);
    releaseLock(attrElementId, 'ATTR');
  }

  function handleAttrChange(index, field, value) {
    const next = [...attrInputs];
    next[index] = { ...next[index], [field]: value };
    setAttrInputs(next);
  }

  // --- Handlers de Métodos / Operaciones ---
  function startEditingMethod(index) {
    if (isLockedByOther) return;
    setEditingMethodIndex(index);
  }

  function finishEditingMethod(index) {
    setEditingMethodIndex(null);
    const updated = methodInputs[index]?.trim();
    if (updated && updateMethod) {
      updateMethod(cls.id, index, updated);
    }
  }

  function handleAddMethod(e) {
    e.stopPropagation();
    if (isLockedByOther) return;
    const newMethod = `operacion_${(cls.methods?.length || 0) + 1}()`;
    if (addMethod) {
      addMethod(cls.id, newMethod);
    }
  }

  function handleRemoveMethod(e, index) {
    e.stopPropagation();
    if (isLockedByOther) return;
    if (removeMethod) {
      removeMethod(cls.id, index);
    }
  }

  function handleMethodChange(index, value) {
    const next = [...methodInputs];
    next[index] = value;
    setMethodInputs(next);
  }

  const cardClasses = [
    'uml-card',
    isDragging ? 'dragging' : '',
    isLockedByOther ? 'locked-by-other' : '',
    isLockedByMe ? 'locked-by-me' : '',
  ].filter(Boolean).join(' ');

  return (
    <div
      className={cardClasses}
      style={{ left: cls.x, top: cls.y }}
      onMouseDown={handleMouseDown}
      role="figure"
      aria-label={`Clase ${cls.name}`}
      data-class-id={cls.id}
    >
      {/* Banner visual de bloqueo por otro usuario */}
      {isLockedByOther && (
        <div className="uml-lock-banner" title={`Bloqueado por ${classLock.lockedByUsername || classLock.lockedByUserId}`}>
          <LockIcon />
          <span>Editando: {classLock.lockedByUsername || classLock.lockedByUserId}</span>
        </div>
      )}

      {/* Banner visual cuando el usuario local tiene el bloqueo */}
      {isLockedByMe && (
        <div className="uml-lock-banner-me">
          <EditIcon />
          <span>Editando (bloqueo activo)</span>
        </div>
      )}

      <div className="uml-card-header">
        {isEditingName ? (
          <input
            className="uml-card-input"
            value={classNameInput}
            onChange={(e) => setClassNameInput(e.target.value)}
            onBlur={finishEditingName}
            autoFocus
            onMouseDown={(e) => e.stopPropagation()}
            onKeyDown={(e) => e.key === 'Enter' && finishEditingName()}
          />
        ) : (
          <span
            className="uml-card-title"
            title={isLockedByOther ? `Bloqueado por ${classLock.lockedByUsername}` : 'Doble click para editar'}
            onDoubleClick={startEditingName}
          >
            {cls.name}
          </span>
        )}
        <span className="uml-card-badge">
          {cls.isIntermediate || cls.name?.startsWith('Detalle_') ? '«intermedia»' : '«entity»'}
        </span>
        <button
          className="uml-card-delete"
          onMouseDown={(e) => e.stopPropagation()}
          onClick={() => !isLockedByOther && deleteClass(cls.id)}
          disabled={isLockedByOther}
          aria-label={`Eliminar clase ${cls.name}`}
          title={isLockedByOther ? 'No disponible: elemento bloqueado' : 'Eliminar'}
        >
          <DeleteIcon />
        </button>
      </div>

      <div className="uml-card-body">
        {attrInputs.length === 0 ? (
          <div className="uml-card-attr" style={{ color: 'var(--color-text-3)', fontStyle: 'italic' }}>
            Sin atributos
          </div>
        ) : (
          attrInputs.map((attr, i) => {
            const attrElementId = `${cls.id}_attr_${i}`;
            const attrLock = locks[attrElementId];
            const isAttrLockedByOther = attrLock && attrLock.locked && attrLock.lockedByUserId !== wsClient.userId;

            return (
              <div
                key={i}
                className="uml-card-attr"
                style={isAttrLockedByOther ? { opacity: 0.5, borderLeft: '2px solid #f59e0b', paddingLeft: '4px' } : {}}
              >
                {editingAttrIndex === i ? (
                  <input
                    className="uml-card-attr-name-input"
                    value={attr.name}
                    onChange={(e) => handleAttrChange(i, 'name', e.target.value)}
                    onBlur={() => finishEditingAttrName(i)}
                    autoFocus
                    onMouseDown={(e) => e.stopPropagation()}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        finishEditingAttrName(i);
                      }
                    }}
                  />
                ) : (
                  <span
                    className="uml-card-attr-name"
                    onDoubleClick={(e) => {
                      e.stopPropagation();
                      if (!isAttrLockedByOther && !isLockedByOther) {
                        startEditingAttrName(i);
                      }
                    }}
                    title={isAttrLockedByOther ? `Atributo bloqueado por ${attrLock?.lockedByUsername || 'otro usuario'}` : 'Doble click para editar nombre'}
                  >
                    +{attr.name}
                  </span>
                )}

                <span className="uml-card-attr-colon">:</span>

                <input
                  type="text"
                  className="uml-card-attr-type-input"
                  value={attr.type ?? ''}
                  onChange={(e) => handleAttrChange(i, 'type', e.target.value)}
                  onFocus={() => {
                    if (!isAttrLockedByOther && !isLockedByOther) {
                      requestLock(attrElementId, 'ATTR');
                    }
                  }}
                  onBlur={() => finishEditingAttrType(i)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      finishEditingAttrType(i);
                      e.currentTarget.blur();
                    }
                  }}
                  onMouseDown={(e) => e.stopPropagation()}
                  disabled={isAttrLockedByOther || isLockedByOther}
                  placeholder="Tipo"
                  title={isAttrLockedByOther ? `Bloqueado por ${attrLock?.lockedByUsername || 'otro usuario'}` : 'Editar tipo libremente (ej: integer, double, date, string...)'}
                />

                {!isAttrLockedByOther && !isLockedByOther && (
                  <button
                    type="button"
                    className="uml-card-attr-remove"
                    onMouseDown={(e) => e.stopPropagation()}
                    onClick={(e) => handleRemoveAttr(e, i, attr.name)}
                    title="Eliminar atributo"
                    aria-label={`Eliminar atributo ${attr.name}`}
                  >
                    ×
                  </button>
                )}
              </div>
            );
          })
        )}

        {/* Botón rápido para agregar atributo atómico */}
        {!isLockedByOther && (
          <button
            type="button"
            className="uml-card-add-attr-btn"
            style={{
              width: '100%',
              marginTop: '4px',
              padding: '2px 6px',
              background: 'rgba(255,255,255,0.04)',
              border: '1px dashed var(--color-border)',
              borderRadius: '4px',
              color: 'var(--color-text-2)',
              fontSize: '11px',
              cursor: 'pointer',
              textAlign: 'center'
            }}
            onClick={handleAddAttr}
          >
            + Atributo
          </button>
        )}
      </div>

      {/* Compartimento de Métodos / Operaciones UML */}
      <div className="uml-card-methods-section" style={{ borderTop: '1px solid var(--color-border)', padding: 'var(--sp-2) var(--sp-3)' }}>
        {methodInputs.length === 0 ? (
          <div className="uml-card-method" style={{ color: 'var(--color-text-3)', fontStyle: 'italic', fontSize: '11px' }}>
            Sin métodos
          </div>
        ) : (
          methodInputs.map((method, mIdx) => (
            <div
              key={mIdx}
              className="uml-card-method"
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '2px 0',
                borderBottom: mIdx < methodInputs.length - 1 ? '1px solid rgba(255,255,255,0.05)' : 'none',
                fontSize: '11px',
                fontFamily: 'var(--font-mono)'
              }}
            >
              {editingMethodIndex === mIdx ? (
                <input
                  className="uml-card-input"
                  value={method}
                  onChange={(e) => handleMethodChange(mIdx, e.target.value)}
                  onBlur={() => finishEditingMethod(mIdx)}
                  autoFocus
                  onMouseDown={(e) => e.stopPropagation()}
                  onKeyDown={(e) => e.key === 'Enter' && finishEditingMethod(mIdx)}
                />
              ) : (
                <div
                  style={{
                    width: '100%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    cursor: isLockedByOther ? 'not-allowed' : 'pointer'
                  }}
                  onDoubleClick={() => !isLockedByOther && startEditingMethod(mIdx)}
                  title={isLockedByOther ? 'Elemento bloqueado' : 'Doble click para editar método'}
                >
                  <span style={{ color: 'var(--color-success, #3ecf8e)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    +{method.startsWith('+') ? method.slice(1) : method}
                  </span>
                  {!isLockedByOther && (
                    <button
                      type="button"
                      style={{
                        background: 'transparent',
                        border: 'none',
                        color: 'var(--color-text-3)',
                        cursor: 'pointer',
                        padding: '0 2px',
                        fontSize: '11px',
                        opacity: 0.6
                      }}
                      onClick={(e) => handleRemoveMethod(e, mIdx)}
                      title="Eliminar método"
                    >
                      ×
                    </button>
                  )}
                </div>
              )}
            </div>
          ))
        )}

        {!isLockedByOther && (
          <button
            type="button"
            className="uml-card-add-method-btn"
            style={{
              width: '100%',
              marginTop: '4px',
              padding: '2px 6px',
              background: 'rgba(255,255,255,0.04)',
              border: '1px dashed var(--color-border)',
              borderRadius: '4px',
              color: 'var(--color-text-2)',
              fontSize: '11px',
              cursor: 'pointer',
              textAlign: 'center'
            }}
            onClick={handleAddMethod}
          >
            + Método
          </button>
        )}
      </div>
    </div>
  );
}

function DeleteIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
      <line x1="18" y1="6" x2="6" y2="18"/>
      <line x1="6" y1="6" x2="18" y2="18"/>
    </svg>
  );
}

function LockIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0 }}>
      <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </svg>
  );
}

function EditIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0 }}>
      <path d="M12 20h9" />
      <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
    </svg>
  );
}

export default memo(UmlClassCard);
